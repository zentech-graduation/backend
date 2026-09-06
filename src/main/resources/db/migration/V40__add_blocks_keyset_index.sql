-- Composite (blocker_id, created_at DESC, blocked_id DESC) index so the blocked-list row-value
-- keyset comparison resolves as an exact index seek instead of reading every block a viewer holds
-- and top-N sorting it. The tiebreaker is blocked_id, the unique key component within a blocker.

CREATE INDEX idx_blocks_blocker_created_blocked
    ON blocks (blocker_id, created_at DESC, blocked_id DESC);
