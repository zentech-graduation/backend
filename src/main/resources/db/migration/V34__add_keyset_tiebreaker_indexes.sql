-- Composite (scope, sort_key DESC, id DESC) indexes so the row-value keyset comparison
-- (created_at, id) < (:cursorTime, :cursorId) resolves as an exact index seek rather than a
-- post-scan filter. The posts index mirrors the partial predicate of idx_posts_user_feed so the
-- feed and profile keyset queries, which filter published and non-deleted rows, can use it.

CREATE INDEX idx_posts_user_created_id
    ON posts (user_id, created_at DESC, id DESC)
    WHERE status = 'published' AND deleted_at IS NULL;

CREATE INDEX idx_post_edit_history_post_edited_id
    ON post_edit_history (post_id, edited_at DESC, id DESC);
