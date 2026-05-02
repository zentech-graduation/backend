-- Flyway migration V07
-- Source: database/schema.sql lines 295-318
-- Comment module: comments (adjacency list with root_id and depth cap), comment_likes.
-- depth is bounded to [0, 10]; root_id points at the top-level ancestor for subtree queries.

CREATE TABLE comments (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id           UUID            REFERENCES comments(id) ON DELETE CASCADE,
    root_id             UUID            REFERENCES comments(id) ON DELETE CASCADE,
    depth               SMALLINT        NOT NULL DEFAULT 0 CHECK (depth BETWEEN 0 AND 10),
    content             TEXT            NOT NULL,
    like_count          INT             NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    reply_count         INT             NOT NULL DEFAULT 0 CHECK (reply_count >= 0),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE comment_likes (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    comment_id          UUID            NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, comment_id)
);
