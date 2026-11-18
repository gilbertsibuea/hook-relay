package dev.hookrelay.ingestion.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox")
public class OutboxEntity {

    public static final String AGGREGATE_EVENT = "event";
    public static final String MESSAGE_EVENT_ACCEPTED = "event.accepted";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEntity() {
    }

    public OutboxEntity(
            String aggregateType,
            UUID aggregateId,
            String messageType,
            String payloadJson,
            Instant createdAt) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.messageType = messageType;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.publishAttempts = 0;
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getPublishAttempts() {
        return publishAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void recordPublishFailure(String error) {
        this.publishAttempts += 1;
        this.lastError = truncate(error, 2000);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
