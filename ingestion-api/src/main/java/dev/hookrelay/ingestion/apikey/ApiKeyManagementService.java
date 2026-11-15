package dev.hookrelay.ingestion.apikey;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookrelay.common.auth.TenantPrincipal;
import dev.hookrelay.common.security.ApiKeyGenerator;
import dev.hookrelay.common.security.ApiKeyHasher;
import dev.hookrelay.common.security.GeneratedApiKey;
import dev.hookrelay.ingestion.audit.AuditService;

@Service
public class ApiKeyManagementService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiKeyHasher apiKeyHasher;
    private final AuditService auditService;
    private final Clock clock;

    public ApiKeyManagementService(
            ApiKeyRepository apiKeyRepository,
            ApiKeyGenerator apiKeyGenerator,
            ApiKeyHasher apiKeyHasher,
            AuditService auditService,
            Clock clock) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyView> list(UUID tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ApiKeyView::from)
                .toList();
    }

    /**
     * Issues a new API key for the tenant. Optionally revokes the caller's current key
     * in the same transaction (rotation). Raw key is returned once and never persisted.
     */
    @Transactional
    public RotatedApiKey rotate(TenantPrincipal actor, boolean revokeCurrent) {
        Instant now = Instant.now(clock);
        GeneratedApiKey generated = apiKeyGenerator.generate();
        UUID newKeyId = UUID.randomUUID();

        ApiKeyEntity created = new ApiKeyEntity(
                newKeyId,
                actor.tenantId(),
                generated.keyPrefix(),
                apiKeyHasher.hash(generated.rawKey()),
                ApiKeyStatus.ACTIVE,
                now,
                null,
                null);
        apiKeyRepository.save(created);

        UUID revokedKeyId = null;
        if (revokeCurrent) {
            ApiKeyEntity current = apiKeyRepository
                    .findByIdAndTenantId(actor.apiKeyId(), actor.tenantId())
                    .orElseThrow(() -> new ApiKeyNotFoundException(actor.apiKeyId()));
            if (current.getStatus() == ApiKeyStatus.ACTIVE) {
                current.revoke(now);
                apiKeyRepository.save(current);
                revokedKeyId = current.getId();
                auditService.record(
                        actor.tenantId(),
                        actor.apiKeyId(),
                        AuditService.ACTION_API_KEY_REVOKED,
                        AuditService.RESOURCE_API_KEY,
                        current.getId(),
                        "{\"reason\":\"rotated\",\"prefix\":\"" + current.getKeyPrefix() + "\"}");
            }
        }

        auditService.record(
                actor.tenantId(),
                actor.apiKeyId(),
                AuditService.ACTION_API_KEY_ROTATED,
                AuditService.RESOURCE_API_KEY,
                newKeyId,
                "{\"prefix\":\"" + generated.keyPrefix()
                        + "\",\"revokedPrevious\":"
                        + (revokedKeyId != null)
                        + "}");

        return new RotatedApiKey(
                ApiKeyView.from(created),
                generated.rawKey(),
                revokedKeyId);
    }

    @Transactional
    public ApiKeyView revoke(TenantPrincipal actor, UUID apiKeyId) {
        ApiKeyEntity apiKey = apiKeyRepository
                .findByIdAndTenantId(apiKeyId, actor.tenantId())
                .orElseThrow(() -> new ApiKeyNotFoundException(apiKeyId));

        if (apiKey.getStatus() == ApiKeyStatus.REVOKED) {
            return ApiKeyView.from(apiKey);
        }

        Instant now = Instant.now(clock);
        apiKey.revoke(now);
        apiKeyRepository.save(apiKey);

        auditService.record(
                actor.tenantId(),
                actor.apiKeyId(),
                AuditService.ACTION_API_KEY_REVOKED,
                AuditService.RESOURCE_API_KEY,
                apiKey.getId(),
                "{\"prefix\":\"" + apiKey.getKeyPrefix() + "\"}");

        return ApiKeyView.from(apiKey);
    }

    public record ApiKeyView(
            UUID id,
            String keyPrefix,
            ApiKeyStatus status,
            Instant createdAt,
            Instant revokedAt,
            Instant lastUsedAt) {

        static ApiKeyView from(ApiKeyEntity entity) {
            return new ApiKeyView(
                    entity.getId(),
                    entity.getKeyPrefix(),
                    entity.getStatus(),
                    entity.getCreatedAt(),
                    entity.getRevokedAt(),
                    entity.getLastUsedAt());
        }
    }

    public record RotatedApiKey(ApiKeyView apiKey, String rawApiKey, UUID revokedApiKeyId) {
        @Override
        public String toString() {
            return "RotatedApiKey[apiKey=" + apiKey
                    + ", rawApiKey=REDACTED, revokedApiKeyId=" + revokedApiKeyId + "]";
        }
    }
}
