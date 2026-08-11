-- Flyway migration V45
-- Record when a comment's content was last changed, so a client can render an "edited" marker.
--
-- comments.updated_at cannot answer that question, for two independent reasons.
--
-- First, trg_comments_updated_at (V16) is a BEFORE UPDATE trigger on the whole row, and
-- like_count and reply_count are columns on that row maintained by trg_comment_like_count and
-- trg_comment_reply_count (V16). A single like therefore moves updated_at on a comment nobody
-- has touched. Second, created_at and updated_at come from two different clocks and are never
-- equal even at insert, so comparing them reports every comment as edited from the moment it
-- exists.
--
-- Narrowing fn_update_updated_at to ignore counter columns was rejected: that function is shared
-- by users, posts, comments and conversations, so changing its meaning to "a column that matters
-- changed" would alter three other tables as a side effect and force every future caller to
-- reason about which columns count. updated_at keeps meaning "this row changed", which is what
-- it has always meant.
--
-- Shape follows the deleted_at precedent: a nullable TIMESTAMPTZ with no default, written
-- explicitly by application code on the edit path only, never by a trigger. NULL means the
-- content has never been changed. No backfill is possible or attempted, because the information
-- does not exist for rows written before this migration; NULL is the honest value for them.
--
-- No edit history is recorded. docs/modules/comment/DATA_RULES.md records that as a deliberate
-- departure from the post module, which keeps post_edit_history (V22). This column answers
-- whether a comment was edited, not what it used to say.
--
-- No index is added. The column is never a filter or a sort key; it is projected alongside a
-- comment row already located by its primary key or by an existing keyset index.

ALTER TABLE comments
  ADD COLUMN edited_at TIMESTAMPTZ;

COMMENT ON COLUMN comments.edited_at IS
  'When the content was last changed by its author. NULL means never edited. Written by '
  'application code on the edit path only; unlike updated_at, no trigger touches it, so a like '
  'or a reply leaves it alone.';
