package dev.hookrelay.ingestion.audit;

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
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_api_key_id")
    private UUID actorApiKeyId;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(
            UUID tenantId,
            UUID actorApiKeyId,
            String action,
            String resourceType,
            UUID resourceId,
            String detailJson,
            Instant createdAt) {
        this.tenantId = tenantId;
        this.actorApiKeyId = actorApiKeyId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detailJson = detailJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getActorApiKeyId() {
        return actorApiKeyId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
