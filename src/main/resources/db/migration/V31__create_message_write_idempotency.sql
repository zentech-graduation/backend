-- Flyway migration V31
-- Message module: HTTP write idempotency. A (user_id, idempotency_key) pair caches the first
-- response so a retried send returns the original result instead of a duplicate message.

CREATE TABLE message_write_idempotency (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key  VARCHAR(64)  NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    response_body    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_message_idempotency_created ON message_write_idempotency (created_at);
