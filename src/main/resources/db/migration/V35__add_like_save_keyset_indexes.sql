-- Composite (scope, created_at DESC, tiebreaker DESC) indexes so the row-value keyset comparison
-- resolves as an exact index seek. The like tiebreaker is user_id and the save tiebreaker is
-- post_id, each the unique key component within its paging scope.

CREATE INDEX idx_post_likes_post_created_user
    ON post_likes (post_id, created_at DESC, user_id DESC);

CREATE INDEX idx_post_saves_user_created_post
    ON post_saves (user_id, created_at DESC, post_id DESC);
