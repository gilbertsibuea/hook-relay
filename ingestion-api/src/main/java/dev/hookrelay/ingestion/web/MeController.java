package dev.hookrelay.ingestion.web;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.hookrelay.common.auth.TenantPrincipal;

/**
 * Lightweight authenticated probe used to verify API key auth during development.
 */
@RestController
@RequestMapping("/v1")
public class MeController {

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        TenantPrincipal principal = (TenantPrincipal) authentication.getPrincipal();
        return new MeResponse(
                principal.tenantId(),
                principal.tenantName(),
                principal.apiKeyId(),
                principal.apiKeyPrefix());
    }

    public record MeResponse(
            UUID tenantId,
            String tenantName,
            UUID apiKeyId,
            String apiKeyPrefix) {
    }
}
