-- Composite (scope, status, created_at DESC, tiebreaker DESC) indexes so the row-value keyset
-- comparison resolves as an exact index seek. The follower list tiebreaks on follower_id and the
-- following list tiebreaks on following_id, each the unique key component within its paging scope.

CREATE INDEX idx_follows_following_created_follower
    ON follows (following_id, status, created_at DESC, follower_id DESC);

CREATE INDEX idx_follows_follower_created_following
    ON follows (follower_id, status, created_at DESC, following_id DESC);
