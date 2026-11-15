package dev.hookrelay.ingestion.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.hookrelay.common.auth.TenantPrincipal;
import dev.hookrelay.ingestion.apikey.ApiKeyManagementService;
import dev.hookrelay.ingestion.apikey.ApiKeyManagementService.ApiKeyView;
import dev.hookrelay.ingestion.apikey.ApiKeyManagementService.RotatedApiKey;
import dev.hookrelay.ingestion.apikey.ApiKeyStatus;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyManagementService apiKeyManagementService;

    public ApiKeyController(ApiKeyManagementService apiKeyManagementService) {
        this.apiKeyManagementService = apiKeyManagementService;
    }

    @GetMapping
    public ApiKeyListResponse list(Authentication authentication) {
        TenantPrincipal tenant = principal(authentication);
        List<ApiKeyResponse> items = apiKeyManagementService.list(tenant.tenantId()).stream()
                .map(ApiKeyResponse::from)
                .toList();
        return new ApiKeyListResponse(items);
    }

    @PostMapping("/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyRotatedResponse rotate(
            Authentication authentication,
            @Valid @RequestBody(required = false) RotateApiKeyRequest request) {
        TenantPrincipal tenant = principal(authentication);
        boolean revokeCurrent = request == null || request.revokeCurrent() == null || request.revokeCurrent();
        RotatedApiKey rotated = apiKeyManagementService.rotate(tenant, revokeCurrent);
        return ApiKeyRotatedResponse.from(rotated);
    }

    @PostMapping("/{apiKeyId}/revoke")
    public ApiKeyResponse revoke(Authentication authentication, @PathVariable UUID apiKeyId) {
        TenantPrincipal tenant = principal(authentication);
        return ApiKeyResponse.from(apiKeyManagementService.revoke(tenant, apiKeyId));
    }

    private static TenantPrincipal principal(Authentication authentication) {
        return (TenantPrincipal) authentication.getPrincipal();
    }

    public record RotateApiKeyRequest(Boolean revokeCurrent) {
    }

    public record ApiKeyResponse(
            UUID id,
            String keyPrefix,
            ApiKeyStatus status,
            Instant createdAt,
            Instant revokedAt,
            Instant lastUsedAt) {

        static ApiKeyResponse from(ApiKeyView view) {
            return new ApiKeyResponse(
                    view.id(),
                    view.keyPrefix(),
                    view.status(),
                    view.createdAt(),
                    view.revokedAt(),
                    view.lastUsedAt());
        }
    }

    public record ApiKeyListResponse(List<ApiKeyResponse> items) {
    }

    public record ApiKeyRotatedResponse(
            UUID id,
            String keyPrefix,
            ApiKeyStatus status,
            Instant createdAt,
            UUID revokedApiKeyId,
            String apiKey) {

        static ApiKeyRotatedResponse from(RotatedApiKey rotated) {
            ApiKeyView view = rotated.apiKey();
            return new ApiKeyRotatedResponse(
                    view.id(),
                    view.keyPrefix(),
                    view.status(),
                    view.createdAt(),
                    rotated.revokedApiKeyId(),
                    rotated.rawApiKey());
        }
    }
}
