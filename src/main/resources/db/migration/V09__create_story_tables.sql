-- Flyway migration V09
-- Source: database/schema.sql lines 353-371
-- Story module: stories (24h ephemeral, default expiry NOW() + 24h), story_views (deduplicated).

CREATE TABLE stories (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_asset_id      UUID            NOT NULL REFERENCES media_assets(id),
    story_type          story_type      NOT NULL DEFAULT 'image',
    caption             TEXT,
    view_count          INT             NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    expires_at          TIMESTAMPTZ     NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE story_views (
    story_id            UUID            NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    viewer_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (story_id, viewer_id)
);
