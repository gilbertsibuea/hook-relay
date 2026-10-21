-- Milestone 0 skeleton. Milestone 1 expands tables and constraints for Core MVP.

CREATE TABLE IF NOT EXISTS schema_bootstrap (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    project     TEXT        NOT NULL DEFAULT 'HookRelay',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO schema_bootstrap (id, project)
VALUES (1, 'HookRelay')
ON CONFLICT (id) DO NOTHING;
