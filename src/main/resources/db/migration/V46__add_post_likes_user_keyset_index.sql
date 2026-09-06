-- Composite (user_id, created_at DESC, post_id DESC) index so the row-value keyset comparison over
-- a single user's likes resolves as an exact index seek. V35 added the equivalent index for
-- post_saves but only the post-scoped one for post_likes, so the user-scoped direction had no
-- index carrying the tiebreaker and had to sort. The tiebreaker is post_id, the unique key
-- component within one user's likes.

CREATE INDEX idx_post_likes_user_created_post
    ON post_likes (user_id, created_at DESC, post_id DESC);
