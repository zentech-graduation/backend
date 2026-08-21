-- Unread counts are otherwise entirely derived from comparing message timestamps against
-- last_read_at, which gives "mark as unread" nothing to change when the caller sent the
-- conversation's own most recent messages themselves - clearing last_read_at finds no newer
-- message from the other side to count. This flag is an independent, purely visual marker the
-- caller sets on themselves, cleared the next time they open the conversation, same as any other
-- messaging app's manual unread toggle.
ALTER TABLE conversation_participants
    ADD COLUMN is_manually_unread BOOLEAN NOT NULL DEFAULT FALSE;
