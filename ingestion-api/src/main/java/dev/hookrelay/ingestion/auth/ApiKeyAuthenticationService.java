package dev.hookrelay.ingestion.auth;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookrelay.common.auth.TenantPrincipal;
import dev.hookrelay.common.security.ApiKeyHasher;
import dev.hookrelay.ingestion.apikey.ApiKeyEntity;
import dev.hookrelay.ingestion.apikey.ApiKeyRepository;
import dev.hookrelay.ingestion.apikey.ApiKeyStatus;
import dev.hookrelay.ingestion.tenant.TenantEntity;
import dev.hookrelay.ingestion.tenant.TenantRepository;
import dev.hookrelay.ingestion.tenant.TenantStatus;

@Service
public class ApiKeyAuthenticationService {

    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;

    public ApiKeyAuthenticationService(
            ApiKeyHasher apiKeyHasher,
            ApiKeyRepository apiKeyRepository,
            TenantRepository tenantRepository) {
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public Optional<TenantPrincipal> authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = apiKeyHasher.hash(rawApiKey.trim());
        Optional<ApiKeyEntity> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);
        if (apiKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        ApiKeyEntity apiKey = apiKeyOpt.get();
        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            return Optional.empty();
        }

        Optional<TenantEntity> tenantOpt = tenantRepository.findById(apiKey.getTenantId());
        if (tenantOpt.isEmpty()) {
            return Optional.empty();
        }

        TenantEntity tenant = tenantOpt.get();
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            return Optional.empty();
        }

        apiKeyRepository.touchLastUsedAt(apiKey.getId());

        return Optional.of(new TenantPrincipal(
                tenant.getId(),
                tenant.getName(),
                apiKey.getId(),
                apiKey.getKeyPrefix()));
    }
}
