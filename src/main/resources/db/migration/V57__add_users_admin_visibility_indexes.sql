-- Indexes for the three access patterns the administrative account surface introduces.
--
-- Runs outside a transaction, declared in the accompanying V57__*.sql.conf, so every build can be
-- CONCURRENTLY. A plain CREATE INDEX holds a lock that blocks writes to users for the whole build,
-- and users is on the write path of registration and of every login.
--
-- Each of the three was measured against a 200,000-row users table with a realistic status, role
-- and email distribution, running the exact statement its endpoint or job issues. All three change
-- the plan; none is kept on the strength of an argument alone.
--
--   idx_users_status_created     status-filtered account list, deep page
--                                without: Incremental Sort over idx_users_created_at, 1,079 rows
--                                         discarded by filter to return 21
--                                with:    single Index Scan, the row-value keyset comparison
--                                         resolved as an index condition, nothing discarded
--                                The discard count scales with both table size and how rare the
--                                status is, so the without-index plan degrades on exactly the
--                                filters an administrator uses most: banned and suspended.
--
--   idx_users_email_trgm         account search on a selective email fragment
--                                without: Parallel Seq Scan, 77.053 ms
--                                with:    BitmapOr across this index and idx_users_username_trgm,
--                                         0.301 ms
--                                Username already had a trigram index; email did not, so an
--                                administrator searching by address paid a full scan of the table.
--
--   idx_users_suspended_until    reinstatement job candidate scan
--                                without: Parallel Seq Scan, 197,001 rows discarded, 11.901 ms
--                                with:    Bitmap Index Scan, 6.937 ms, index is 48 kB
--                                Partial on suspended_until IS NOT NULL, so an indefinite
--                                suspension is absent from the index rather than filtered out of it.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_trgm
    ON users USING gin (email gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_status_created
    ON users (status, created_at DESC, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_suspended_until
    ON users (suspended_until) WHERE suspended_until IS NOT NULL;
