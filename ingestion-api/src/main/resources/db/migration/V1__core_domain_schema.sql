-- HookRelay core domain schema (HR-005).
-- Deliveries / delivery_attempts arrive in a later migration (HR-014).
--
-- Design notes:
-- - Endpoint subscriptions use a relational table (not a Postgres array) so
--   fan-out can index and query by event_type efficiently.
-- - No ON DELETE CASCADE on tenant-owned business rows; avoid wiping audit
--   history by accident. Soft-delete endpoints via deleted_at.
-- - API key raw values are never stored; only key_prefix + key_hash.
-- - Endpoint signing secrets are stored encrypted (ciphertext + nonce).

CREATE TABLE tenants (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    status      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tenants_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT tenants_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_tenants_status ON tenants (status);

CREATE TABLE api_keys (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE RESTRICT,
    key_prefix   TEXT        NOT NULL,
    key_hash     TEXT        NOT NULL,
    status       TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT api_keys_prefix_not_blank CHECK (length(btrim(key_prefix)) > 0),
    CONSTRAINT api_keys_hash_not_blank CHECK (length(btrim(key_hash)) > 0),
    CONSTRAINT api_keys_status_valid CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT api_keys_revoked_consistency CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL)
        OR (status = 'ACTIVE' AND revoked_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_api_keys_key_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_tenant_status ON api_keys (tenant_id, status);
CREATE INDEX idx_api_keys_prefix ON api_keys (key_prefix);

CREATE TABLE endpoints (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID        NOT NULL REFERENCES tenants (id) ON DELETE RESTRICT,
    name                       TEXT        NOT NULL,
    url                        TEXT        NOT NULL,
    status                     TEXT        NOT NULL,
    signing_secret_ciphertext  BYTEA       NOT NULL,
    signing_secret_nonce       BYTEA       NOT NULL,
    signing_secret_key_id      TEXT        NOT NULL DEFAULT 'local-v1',
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                 TIMESTAMPTZ,
    CONSTRAINT endpoints_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT endpoints_url_not_blank CHECK (length(btrim(url)) > 0),
    CONSTRAINT endpoints_status_valid CHECK (status IN ('ACTIVE', 'PAUSED', 'DISABLED')),
    CONSTRAINT endpoints_secret_nonce_present CHECK (octet_length(signing_secret_nonce) > 0),
    CONSTRAINT endpoints_secret_ciphertext_present CHECK (octet_length(signing_secret_ciphertext) > 0)
);

CREATE INDEX idx_endpoints_tenant_status ON endpoints (tenant_id, status);
CREATE INDEX idx_endpoints_tenant_active
    ON endpoints (tenant_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

-- One row per (endpoint, event_type) subscription for fan-out lookups.
CREATE TABLE endpoint_subscriptions (
    id            UUID PRIMARY KEY,
    endpoint_id   UUID        NOT NULL REFERENCES endpoints (id) ON DELETE RESTRICT,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id) ON DELETE RESTRICT,
    event_type    TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT endpoint_subscriptions_event_type_not_blank CHECK (length(btrim(event_type)) > 0)
);

CREATE UNIQUE INDEX uq_endpoint_subscriptions_endpoint_event
    ON endpoint_subscriptions (endpoint_id, event_type);
CREATE INDEX idx_endpoint_subscriptions_tenant_event
    ON endpoint_subscriptions (tenant_id, event_type);
CREATE INDEX idx_endpoint_subscriptions_event_type
    ON endpoint_subscriptions (event_type);

CREATE TABLE events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id) ON DELETE RESTRICT,
    event_type    TEXT        NOT NULL,
    payload_json  JSONB       NOT NULL,
    status        TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ,
    CONSTRAINT events_type_not_blank CHECK (length(btrim(event_type)) > 0),
    CONSTRAINT events_status_valid CHECK (status IN ('ACCEPTED'))
);

CREATE INDEX idx_events_tenant_created_at ON events (tenant_id, created_at DESC);
CREATE INDEX idx_events_tenant_type_created_at ON events (tenant_id, event_type, created_at DESC);

CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id) ON DELETE RESTRICT,
    idempotency_key  TEXT        NOT NULL,
    request_hash     TEXT        NOT NULL,
    event_id         UUID        NOT NULL REFERENCES events (id) ON DELETE RESTRICT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ,
    CONSTRAINT idempotency_keys_key_not_blank CHECK (length(btrim(idempotency_key)) > 0),
    CONSTRAINT idempotency_keys_hash_not_blank CHECK (length(btrim(request_hash)) > 0)
);

CREATE UNIQUE INDEX uq_idempotency_keys_tenant_key
    ON idempotency_keys (tenant_id, idempotency_key);
CREATE INDEX idx_idempotency_keys_event_id ON idempotency_keys (event_id);
CREATE INDEX idx_idempotency_keys_expires_at
    ON idempotency_keys (expires_at)
    WHERE expires_at IS NOT NULL;

-- Generic transactional outbox for event.accepted and future delivery commands.
CREATE TABLE outbox (
    id                BIGSERIAL PRIMARY KEY,
    aggregate_type    TEXT        NOT NULL,
    aggregate_id      UUID        NOT NULL,
    message_type      TEXT        NOT NULL,
    payload_json      JSONB       NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at      TIMESTAMPTZ,
    publish_attempts  INTEGER     NOT NULL DEFAULT 0,
    last_error        TEXT,
    CONSTRAINT outbox_aggregate_type_not_blank CHECK (length(btrim(aggregate_type)) > 0),
    CONSTRAINT outbox_message_type_not_blank CHECK (length(btrim(message_type)) > 0),
    CONSTRAINT outbox_publish_attempts_non_negative CHECK (publish_attempts >= 0)
);

CREATE INDEX idx_outbox_unpublished_created_at
    ON outbox (created_at)
    WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate
    ON outbox (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_message_type_created_at
    ON outbox (message_type, created_at);
