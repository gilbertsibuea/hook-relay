package dev.hookrelay.ingestion.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import dev.hookrelay.ingestion.config.OutboxProperties;

@Component
@ConditionalOnBean(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "hookrelay.outbox", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    public KafkaOutboxMessagePublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties outboxProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxProperties = outboxProperties;
    }

    @Override
    public void publish(OutboxEntity record) {
        String topic = resolveTopic(record.getMessageType());
        String key = record.getAggregateId().toString();
        kafkaTemplate.send(topic, key, record.getPayloadJson()).join();
    }

    private String resolveTopic(String messageType) {
        if (OutboxEntity.MESSAGE_EVENT_ACCEPTED.equals(messageType)) {
            return outboxProperties.getEventsAcceptedTopic();
        }
        return outboxProperties.getEventsAcceptedTopic();
    }
}
