-- Records the status a post held before a moderator removed it, so restoring returns it there.
--
-- Restore previously set every post it touched to 'published'. A draft or an archived post removed
-- by moderation therefore came back visible to everyone, which is a publication the owner never
-- asked for and the moderator did not intend either.
--
-- The column is written by the removal arm and cleared by the restore arm, so it is non-null only
-- while a post sits removed by moderation. It stays NULL for a post the owner removed, because that
-- path has no restore.
--
-- Deliberately not read from admin_actions.metadata. The audit log records what happened; making
-- application behaviour depend on it would turn an append-only record into a load-bearing store,
-- and a row whose actor was later deleted would still have to answer the question. NULL here means
-- the post was removed before this column existed, and restore falls back to 'published' for it.

ALTER TABLE posts ADD COLUMN status_before_moderation post_status;
