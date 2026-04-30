-- Flyway migration V03
-- Source: database/schema.sql lines 146-169
-- User profile and settings module: user_settings, push_tokens.

CREATE TABLE user_settings (
    user_id                 UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notify_likes            BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_comments         BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_follows          BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_mentions         BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_messages         BOOLEAN     NOT NULL DEFAULT TRUE,
    show_activity_status    BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_story_replies     BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_message_requests  BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE push_tokens (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token               TEXT            NOT NULL UNIQUE,
    platform            VARCHAR(10)     NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_used_at        TIMESTAMPTZ
);
