package dev.hookrelay.ingestion.provision;

import java.util.UUID;

/**
 * Result of provisioning a development tenant. {@code rawApiKey} is shown once and never stored.
 */
public record ProvisionedTenant(
        UUID tenantId,
        String tenantName,
        UUID apiKeyId,
        String apiKeyPrefix,
        String rawApiKey) {

    @Override
    public String toString() {
        return "ProvisionedTenant[tenantId=" + tenantId
                + ", tenantName=" + tenantName
                + ", apiKeyId=" + apiKeyId
                + ", apiKeyPrefix=" + apiKeyPrefix
                + ", rawApiKey=REDACTED]";
    }
}
