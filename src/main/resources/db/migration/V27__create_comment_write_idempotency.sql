-- Flyway migration V26
-- Comment module: HTTP write idempotency. A (user_id, idempotency_key) pair caches the first
-- response so a retried create returns the original result instead of a duplicate comment.

CREATE TABLE comment_write_idempotency (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key  VARCHAR(64)  NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    response_body    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_comment_idempotency_created ON comment_write_idempotency (created_at);
