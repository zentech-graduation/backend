-- Serves "everything done to this account" on the moderation audit log.
--
-- Runs outside a transaction, declared in the accompanying .sql.conf, so the build is
-- CONCURRENTLY.
--
-- idx_admin_actions_target (V15) leads with target_user_id but carries no ordering columns, so the
-- planner either walked the time-ordered index filtering on the target, or combined the two
-- indexes with a BitmapAnd and then sorted. Both plans change with this index.
--
-- Measured on 100,000 rows over 200 distinct targets, against the exact statement the endpoint
-- issues, EXPLAIN (ANALYZE, BUFFERS):
--
--   everything done to one account, first page of 21
--     without: Index Scan using idx_admin_actions_created, 80 buffers, 0.711 ms
--     with:    Index Scan using idx_admin_actions_target_created, 24 buffers, 0.074 ms
--
--   the same bounded to a seven-day window
--     without: BitmapAnd of idx_admin_actions_target and idx_admin_actions_created, then Sort,
--              38 buffers, 0.471 ms
--     with:    Bitmap Index Scan on idx_admin_actions_target_created, then Sort,
--              22 buffers, 0.094 ms
--
-- No index was added for the time bound on its own. That case was measured too and the plan did
-- not change: idx_admin_actions_created (V74) already serves a pure window as an index scan
-- touching 4 buffers, with and without this index. An index that does not change a plan is not
-- worth the write cost on an append-only table.
--
-- Partial on target_user_id IS NOT NULL, matching V15: an action with no user target, such as a
-- hashtag decision, is never reached through this filter.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_admin_actions_target_created
    ON admin_actions (target_user_id, created_at DESC, id DESC)
    WHERE target_user_id IS NOT NULL;
