package dev.hookrelay.ingestion.auth;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import dev.hookrelay.common.auth.TenantPrincipal;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final TenantPrincipal principal;

    public ApiKeyAuthenticationToken(TenantPrincipal principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public TenantPrincipal getPrincipal() {
        return principal;
    }
}
