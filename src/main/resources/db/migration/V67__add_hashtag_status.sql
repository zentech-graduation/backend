-- Gives a hashtag a lifecycle, so a term can be taken out of discovery without touching the posts
-- that used it.
--
-- Three values, lowercase, matching every other enum in this schema:
--   active   the default and the only state a hashtag reaches on its own, through first use
--   banned   hidden from every hashtag surface, and refused on every post write path
--   deleted  hidden from every hashtag surface and from the post detail listing
--
-- Deleted is a state, not a row removal. Deleting the row would cascade to post_hashtags, and the
-- trg_hashtag_post_count trigger would then rewrite hashtags.post_count for every post that used
-- the tag. That destroys history nothing asked to destroy, and it cannot be undone.
--
-- CREATE TYPE and the ALTER TABLE that uses the new type share this file deliberately. The
-- restriction that keeps ALTER TYPE ... ADD VALUE alone in a migration does not apply to CREATE
-- TYPE: a type created inside a transaction is usable by later statements in that same
-- transaction. Verified against both PostgreSQL versions this project runs, 18.6 in the compose
-- stack and 16.15 in the Testcontainers image, by creating the type, adding a column of that type
-- with a default, and writing a non-default value, all inside one explicit transaction.
--
-- status_by is ON DELETE SET NULL, matching reports.reviewed_by and admin_actions.admin_id:
-- deleting the administrator's account must not delete the hashtag decision it made.
--
-- Every column is nullable or carries a default, so this is a catalogue-only change. The two
-- indexes that name the new column live in V68, which runs outside a transaction so the builds can
-- be CONCURRENTLY.

CREATE TYPE hashtag_status AS ENUM ('active', 'banned', 'deleted');

ALTER TABLE hashtags ADD COLUMN status      hashtag_status NOT NULL DEFAULT 'active';
ALTER TABLE hashtags ADD COLUMN status_note TEXT;
ALTER TABLE hashtags ADD COLUMN status_at   TIMESTAMPTZ;
ALTER TABLE hashtags ADD COLUMN status_by   UUID REFERENCES users(id) ON DELETE SET NULL;
