-- Audit trail for security-sensitive actions (API key rotate/revoke, etc.).

CREATE TABLE audit_events (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE RESTRICT,
    actor_api_key_id UUID        REFERENCES api_keys (id) ON DELETE SET NULL,
    action           TEXT        NOT NULL,
    resource_type    TEXT        NOT NULL,
    resource_id      UUID,
    detail_json      JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_events_action_not_blank CHECK (length(btrim(action)) > 0),
    CONSTRAINT audit_events_resource_type_not_blank CHECK (length(btrim(resource_type)) > 0)
);

CREATE INDEX idx_audit_events_tenant_created_at
    ON audit_events (tenant_id, created_at DESC);
CREATE INDEX idx_audit_events_resource
    ON audit_events (resource_type, resource_id);
