# Threat Model (Bootstrap Draft)

This is a living draft. Milestone 3 hardens and tests the controls below.

## Assets

- Tenant API keys
- Endpoint signing secrets
- Event payloads
- Delivery attempt history and admin query access
- Encryption keys and infrastructure credentials

## Primary threats

| Threat | Mitigation direction |
|---|---|
| Stolen API key | High-entropy keys, hashed storage, rotation/revocation |
| SSRF via webhook URL | HTTPS-only in prod, DNS/IP denylist, redirects off, revalidation |
| Cross-tenant data access | Tenant identity from auth context; scoped queries/tests |
| Secret leakage in logs/metrics | Redaction; no secrets as Prometheus labels |
| Dependency/supply-chain risk | Pinned versions, CI dependency/secret scanning |

## Residual risk

DNS rebinding and compromised egress paths are not fully eliminated by application checks alone. Environment network policy remains part of the control set.
