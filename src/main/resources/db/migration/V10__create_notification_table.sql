-- Flyway migration V10
-- Source: database/schema.sql lines 377-388
-- Notification module: in-app notifications for social events.

CREATE TABLE notifications (
    id                  UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        UUID                NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_id            UUID                REFERENCES users(id) ON DELETE SET NULL,
    type                notification_type   NOT NULL,
    entity_type         VARCHAR(50),
    entity_id           UUID,
    is_read             BOOLEAN             NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);
