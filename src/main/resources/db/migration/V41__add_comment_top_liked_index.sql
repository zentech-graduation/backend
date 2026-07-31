-- Serves the pinned "top comments" block on the first page of a post's comment list:
-- the three most-liked eligible top-level comments. Without this index the ordering is a
-- parallel sequential scan plus a top-N heapsort, because no existing comment index carries
-- like_count in any position.
--
-- like_count DESC leads the ordering columns; created_at DESC and id DESC make the sort total,
-- since like_count ties are common and created_at is not unique. The partial predicate mirrors
-- the query's eligibility filter exactly so the planner can prove implication and the index
-- stays small - it excludes replies, soft-deleted rows, and non-approved rows.

CREATE INDEX idx_comments_post_top_liked
    ON comments (post_id, like_count DESC, created_at DESC, id DESC)
    WHERE parent_id IS NULL AND deleted_at IS NULL AND moderation_status = 'approved';
