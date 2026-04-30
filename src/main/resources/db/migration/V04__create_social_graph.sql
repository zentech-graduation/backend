-- Flyway migration V04
-- Source: database/schema.sql lines 176-193
-- Social graph module: follows (with pending state for private accounts), blocks.

CREATE TABLE follows (
    follower_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id        UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status              follow_status   NOT NULL DEFAULT 'accepted',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CHECK (follower_id <> following_id)
);

CREATE TABLE blocks (
    blocker_id          UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id          UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
