package dev.hookrelay.ingestion.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookrelay.ingestion.config.OutboxProperties;

/**
 * Publishes unpublished outbox rows to Kafka. Safe to restart: unpublished rows are retried.
 */
@Service
@ConditionalOnProperty(prefix = "hookrelay.outbox", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxRepository outboxRepository;
    private final OutboxMessagePublisher publisher;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    public OutboxRelayService(
            OutboxRepository outboxRepository,
            OutboxMessagePublisher publisher,
            OutboxProperties outboxProperties,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hookrelay.outbox.poll-interval-ms:1000}")
    public void pollAndPublish() {
        publishPendingBatch();
    }

    @Transactional
    public int publishPendingBatch() {
        List<OutboxEntity> batch = outboxRepository.findUnpublished(
                PageRequest.of(0, outboxProperties.getBatchSize()));
        int published = 0;
        for (OutboxEntity record : batch) {
            try {
                publisher.publish(record);
                record.markPublished(Instant.now(clock));
                outboxRepository.save(record);
                published++;
            } catch (RuntimeException ex) {
                record.recordPublishFailure(ex.getMessage());
                outboxRepository.save(record);
                log.warn(
                        "Failed to publish outbox id={} type={} attempts={}",
                        record.getId(),
                        record.getMessageType(),
                        record.getPublishAttempts(),
                        ex);
            }
        }
        return published;
    }
}
