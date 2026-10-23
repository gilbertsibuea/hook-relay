package dev.hookrelay.ingestion.endpoint;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "endpoint_subscriptions")
public class EndpointSubscriptionEntity {

    @Id
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EndpointSubscriptionEntity() {
    }

    public EndpointSubscriptionEntity(
            UUID id,
            UUID endpointId,
            UUID tenantId,
            String eventType,
            Instant createdAt) {
        this.id = id;
        this.endpointId = endpointId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
