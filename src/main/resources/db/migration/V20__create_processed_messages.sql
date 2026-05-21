CREATE TABLE processed_messages (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_name   VARCHAR(100)    NOT NULL,
    event_id        UUID            NOT NULL,
    event_type      VARCHAR(150)    NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (consumer_name, event_id)
);

CREATE INDEX idx_processed_messages_event
    ON processed_messages (event_id);

CREATE INDEX idx_processed_messages_processed_at
    ON processed_messages (processed_at);
