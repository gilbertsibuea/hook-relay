package dev.hookrelay.common.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated tenant identity resolved from an API key.
 */
public record TenantPrincipal(
        UUID tenantId,
        String tenantName,
        UUID apiKeyId,
        String apiKeyPrefix) {

    public TenantPrincipal {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(tenantName, "tenantName");
        Objects.requireNonNull(apiKeyId, "apiKeyId");
        Objects.requireNonNull(apiKeyPrefix, "apiKeyPrefix");
    }
}
