-- Flyway migration V05
-- Source: database/schema.sql lines 205-218
-- Media module: media_assets (CDN-backed image/video metadata).

CREATE TABLE media_assets (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_key         TEXT            NOT NULL UNIQUE,
    cdn_url             TEXT            NOT NULL,
    media_type          media_type      NOT NULL,
    mime_type           VARCHAR(100)    NOT NULL,
    file_size           BIGINT          NOT NULL CHECK (file_size > 0),
    width               INT             CHECK (width > 0),
    height              INT             CHECK (height > 0),
    duration            INT             CHECK (duration >= 0),
    blurhash            VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
