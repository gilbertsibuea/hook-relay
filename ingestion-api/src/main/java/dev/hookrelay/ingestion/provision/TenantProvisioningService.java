package dev.hookrelay.ingestion.provision;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookrelay.common.security.ApiKeyGenerator;
import dev.hookrelay.common.security.ApiKeyHasher;
import dev.hookrelay.common.security.GeneratedApiKey;
import dev.hookrelay.ingestion.apikey.ApiKeyEntity;
import dev.hookrelay.ingestion.apikey.ApiKeyRepository;
import dev.hookrelay.ingestion.apikey.ApiKeyStatus;
import dev.hookrelay.ingestion.tenant.TenantEntity;
import dev.hookrelay.ingestion.tenant.TenantRepository;
import dev.hookrelay.ingestion.tenant.TenantStatus;

@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiKeyHasher apiKeyHasher;
    private final Clock clock;

    public TenantProvisioningService(
            TenantRepository tenantRepository,
            ApiKeyRepository apiKeyRepository,
            ApiKeyGenerator apiKeyGenerator,
            ApiKeyHasher apiKeyHasher,
            Clock clock) {
        this.tenantRepository = tenantRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
        this.clock = clock;
    }

    @Transactional
    public ProvisionedTenant provision(String tenantName) {
        String normalizedName = requireName(tenantName);
        Instant now = Instant.now(clock);
        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();

        TenantEntity tenant = new TenantEntity(
                tenantId,
                normalizedName,
                TenantStatus.ACTIVE,
                now,
                now);
        tenantRepository.save(tenant);

        GeneratedApiKey generated = apiKeyGenerator.generate();
        String keyHash = apiKeyHasher.hash(generated.rawKey());

        ApiKeyEntity apiKey = new ApiKeyEntity(
                apiKeyId,
                tenantId,
                generated.keyPrefix(),
                keyHash,
                ApiKeyStatus.ACTIVE,
                now,
                null,
                null);
        apiKeyRepository.save(apiKey);

        return new ProvisionedTenant(
                tenantId,
                normalizedName,
                apiKeyId,
                generated.keyPrefix(),
                generated.rawKey());
    }

    private static String requireName(String tenantName) {
        if (tenantName == null || tenantName.isBlank()) {
            throw new IllegalArgumentException("tenant name must not be blank");
        }
        return tenantName.trim();
    }
}
