package dev.hookrelay.ingestion.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.hookrelay.ingestion.event.EventIngestionService;
import dev.hookrelay.ingestion.provision.ProvisionedTenant;
import dev.hookrelay.ingestion.provision.TenantProvisioningService;
import dev.hookrelay.ingestion.support.PostgresTestcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "hookrelay.outbox.relay-enabled=true",
                "hookrelay.outbox.poll-interval-ms=60000"
        })
class OutboxRelayIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
        // Override the shared helper which disables the relay for most tests.
        registry.add("hookrelay.outbox.relay-enabled", () -> "true");
    }

    @Autowired
    private TenantProvisioningService provisioningService;

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private RecordingOutboxMessagePublisher recordingPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishesPendingOutboxAndResumesAfterPartialFailure() {
        ProvisionedTenant tenant = provisioningService.provision("Outbox Tenant");
        ObjectNode payload = objectMapper.createObjectNode().put("orderId", "o-1");

        var accepted = eventIngestionService.accept(
                tenant.tenantId(),
                "outbox-key-1",
                "payment.succeeded",
                payload);

        Integer pendingBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = ?::uuid",
                Integer.class,
                accepted.eventId());
        assertThat(pendingBefore).isOne();

        recordingPublisher.failNext(1);
        int firstPass = outboxRelayService.publishPendingBatch();
        assertThat(firstPass).isZero();

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT publish_attempts FROM outbox WHERE aggregate_id = ?::uuid",
                Integer.class,
                accepted.eventId());
        assertThat(attempts).isGreaterThanOrEqualTo(1);

        int secondPass = outboxRelayService.publishPendingBatch();
        assertThat(secondPass).isEqualTo(1);

        Integer pendingAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE published_at IS NULL AND aggregate_id = ?::uuid",
                Integer.class,
                accepted.eventId());
        assertThat(pendingAfter).isZero();

        List<OutboxEntity> published = recordingPublisher.published();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().getAggregateId()).isEqualTo(accepted.eventId());
        assertThat(published.getFirst().getMessageType()).isEqualTo(OutboxEntity.MESSAGE_EVENT_ACCEPTED);
    }

    @TestConfiguration
    static class RecordingPublisherConfig {
        @Bean
        @Primary
        RecordingOutboxMessagePublisher recordingOutboxMessagePublisher() {
            return new RecordingOutboxMessagePublisher();
        }
    }

    static final class RecordingOutboxMessagePublisher implements OutboxMessagePublisher {
        private final List<OutboxEntity> published = new CopyOnWriteArrayList<>();
        private int failuresRemaining;

        synchronized void failNext(int count) {
            this.failuresRemaining = count;
        }

        List<OutboxEntity> published() {
            return new ArrayList<>(published);
        }

        @Override
        public synchronized void publish(OutboxEntity record) {
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("simulated broker outage");
            }
            published.add(record);
        }
    }
}
