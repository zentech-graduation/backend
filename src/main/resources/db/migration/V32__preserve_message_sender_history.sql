-- Flyway migration V32
-- Preserves conversation history for remaining participants when the sending user's account is
-- deleted. sender_id was originally ON DELETE CASCADE, which destroyed every message a deleted
-- user had ever sent instead of leaving a "deleted user" tombstone.

ALTER TABLE messages
    DROP CONSTRAINT messages_sender_id_fkey;

ALTER TABLE messages
    ALTER COLUMN sender_id DROP NOT NULL;

ALTER TABLE messages
    ADD CONSTRAINT messages_sender_id_fkey
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE SET NULL;
