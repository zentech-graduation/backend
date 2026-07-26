-- Composite (scope, created_at DESC, id DESC) indexes so the row-value keyset comparison
-- (created_at, id) < (:cursorTime, :cursorId) resolves as an exact index seek. Each mirrors the
-- partial predicate of the existing comment index it extends.

CREATE INDEX idx_comments_post_root_id
    ON comments (post_id, created_at DESC, id DESC)
    WHERE parent_id IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_comments_parent_id
    ON comments (parent_id, created_at DESC, id DESC)
    WHERE parent_id IS NOT NULL AND deleted_at IS NULL;
