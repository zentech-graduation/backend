-- Per-participant, per-conversation customization: pinning a conversation to the top of the
-- list, muting its notifications, and privately renaming the other person for this thread only.
-- All three are nullable/defaulted additive columns on an existing table, so this runs as a fast
-- metadata-only change with no table rewrite and no lock beyond the brief one any DDL takes.
ALTER TABLE conversation_participants
    ADD COLUMN pinned_at TIMESTAMPTZ NULL,
    ADD COLUMN is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN nickname VARCHAR(50) NULL;
