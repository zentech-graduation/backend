ALTER TABLE outbox_events
    ADD COLUMN claim_id UUID,
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN claimed_until TIMESTAMPTZ;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_events_processing_claim
        CHECK (
            status <> 'PROCESSING'
            OR (
                claim_id IS NOT NULL
                AND claimed_at IS NOT NULL
                AND claimed_until IS NOT NULL
                AND claimed_until > claimed_at
            )
        );
