-- Flyway migration V08
-- Source: database/schema.sql lines 324-347
-- Hashtag module: hashtags, post_hashtags, hashtag_trending.

CREATE TABLE hashtags (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    UNIQUE NOT NULL,
    post_count          INT             NOT NULL DEFAULT 0 CHECK (post_count >= 0),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE post_hashtags (
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    hashtag_id          UUID            NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, hashtag_id)
);

CREATE TABLE hashtag_trending (
    hashtag_id          UUID            NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    period_start        TIMESTAMPTZ     NOT NULL,
    period_end          TIMESTAMPTZ     NOT NULL,
    post_count          INT             NOT NULL DEFAULT 0,
    rank                INT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (hashtag_id, period_start)
);
