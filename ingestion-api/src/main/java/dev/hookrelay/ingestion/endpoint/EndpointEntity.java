package dev.hookrelay.ingestion.endpoint;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "endpoints")
public class EndpointEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EndpointStatus status;

    @Column(name = "signing_secret_ciphertext", nullable = false)
    private byte[] signingSecretCiphertext;

    @Column(name = "signing_secret_nonce", nullable = false)
    private byte[] signingSecretNonce;

    @Column(name = "signing_secret_key_id", nullable = false)
    private String signingSecretKeyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected EndpointEntity() {
    }

    public EndpointEntity(
            UUID id,
            UUID tenantId,
            String name,
            String url,
            EndpointStatus status,
            byte[] signingSecretCiphertext,
            byte[] signingSecretNonce,
            String signingSecretKeyId,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.url = url;
        this.status = status;
        this.signingSecretCiphertext = signingSecretCiphertext;
        this.signingSecretNonce = signingSecretNonce;
        this.signingSecretKeyId = signingSecretKeyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public byte[] getSigningSecretCiphertext() {
        return signingSecretCiphertext;
    }

    public byte[] getSigningSecretNonce() {
        return signingSecretNonce;
    }

    public String getSigningSecretKeyId() {
        return signingSecretKeyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void rename(String name, Instant updatedAt) {
        this.name = name;
        this.updatedAt = updatedAt;
    }

    public void changeUrl(String url, Instant updatedAt) {
        this.url = url;
        this.updatedAt = updatedAt;
    }

    public void changeStatus(EndpointStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void deactivate(Instant deletedAt) {
        this.status = EndpointStatus.DISABLED;
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }
}
