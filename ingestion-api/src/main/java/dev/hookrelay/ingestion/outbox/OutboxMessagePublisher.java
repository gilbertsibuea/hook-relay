package dev.hookrelay.ingestion.outbox;

/**
 * Publishes an outbox record to the transport (Kafka).
 */
public interface OutboxMessagePublisher {

    void publish(OutboxEntity record);
}
