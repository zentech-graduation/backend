-- Flyway migration V13
-- Source: database/schema.sql lines 457-468
-- Admin module: moderation audit log (ban, suspend, remove, restore, resolve_report).

CREATE TABLE admin_actions (
    id                      UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id                UUID                NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action_type             admin_action_type   NOT NULL,
    target_user_id          UUID                REFERENCES users(id) ON DELETE SET NULL,
    target_entity_type      VARCHAR(50),
    target_entity_id        UUID,
    report_id               UUID                REFERENCES reports(id) ON DELETE SET NULL,
    reason                  TEXT,
    metadata                JSONB,
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);
