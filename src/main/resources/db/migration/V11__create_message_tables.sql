-- Flyway migration V11
-- Source: database/schema.sql lines 395-433
-- Message module: conversations (1-1 or group), conversation_participants, messages.

CREATE TABLE conversations (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group            BOOLEAN         NOT NULL DEFAULT FALSE,
    group_name          VARCHAR(100),
    group_avatar_url    TEXT,
    created_by          UUID            REFERENCES users(id) ON DELETE SET NULL,
    last_message_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE conversation_participants (
    conversation_id     UUID            NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_admin            BOOLEAN         NOT NULL DEFAULT FALSE,
    joined_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    left_at             TIMESTAMPTZ,
    last_read_at        TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE messages (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID            NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type        message_type    NOT NULL DEFAULT 'text',
    content             TEXT,
    media_asset_id      UUID            REFERENCES media_assets(id),
    shared_post_id      UUID            REFERENCES posts(id) ON DELETE SET NULL,
    shared_story_id     UUID            REFERENCES stories(id) ON DELETE SET NULL,
    reply_to_id         UUID            REFERENCES messages(id) ON DELETE SET NULL,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
