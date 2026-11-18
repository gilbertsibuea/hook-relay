package dev.hookrelay.ingestion.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(
            UUID id,
            UUID tenantId,
            String idempotencyKey,
            String requestHash,
            UUID eventId,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
