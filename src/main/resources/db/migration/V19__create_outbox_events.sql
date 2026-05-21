CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID            NOT NULL UNIQUE,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      VARCHAR(150)    NOT NULL,
    routing_key     VARCHAR(150)    NOT NULL,
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD')),
    attempt_count   INTEGER         NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_retry_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    )
);

CREATE INDEX idx_outbox_events_publish_scan
    ON outbox_events (status, next_retry_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at);
