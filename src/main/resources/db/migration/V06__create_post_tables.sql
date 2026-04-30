-- Flyway migration V06
-- Source: database/schema.sql lines 224-280
-- Post module: posts, post_media (carousel), post_user_tags, post_likes, post_saves.
-- Counter columns default to 0 and are maintained by triggers added in V16.

CREATE TABLE posts (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    caption             TEXT,
    post_type           post_type       NOT NULL DEFAULT 'image',
    status              post_status     NOT NULL DEFAULT 'published',
    like_count          INT             NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count       INT             NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    save_count          INT             NOT NULL DEFAULT 0 CHECK (save_count >= 0),
    view_count          INT             NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    location_name       VARCHAR(255),
    latitude            DECIMAL(10,8),
    longitude           DECIMAL(11,8),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE post_media (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    media_asset_id      UUID            NOT NULL REFERENCES media_assets(id),
    position            SMALLINT        NOT NULL DEFAULT 0,
    alt_text            TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, position)
);

CREATE TABLE post_user_tags (
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tagged_user_id      UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_asset_id      UUID            REFERENCES media_assets(id),
    x_position          DECIMAL(5,2),
    y_position          DECIMAL(5,2),
    PRIMARY KEY (post_id, tagged_user_id)
);

CREATE TABLE post_likes (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);

CREATE TABLE post_saves (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);
