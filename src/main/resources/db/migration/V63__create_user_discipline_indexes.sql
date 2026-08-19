-- Indexes for the three access patterns the discipline ladder introduces.
--
-- Runs outside a transaction, declared in the accompanying V63__*.sql.conf, so every build can be
-- CONCURRENTLY. The tables were created empty by V62, so the availability argument that makes
-- CONCURRENTLY mandatory does not bite here; the statements are written that way anyway because a
-- rule with a case-by-case exemption stops being checkable, and the cost on an empty table is nil.
--
-- Measured against 200,400 warning rows across 40,000 accounts, 5 per cent revoked, with one
-- account holding 400 warnings inside the 90-day window. Each statement below is the one the
-- corresponding code path issues.
--
--   idx_user_warnings_active     active-warning count, and the violations listing
--                                without: Parallel Seq Scan, 66,667 rows discarded per worker
--                                         count 11.3 ms, first page 10.4 ms, deep page 9.9 ms
--                                with:    Bitmap Index Scan for the count, Index Scan for the page
--                                         count 0.50 ms, first page 0.11 ms, deep page 0.08 ms
--                                The discard count scales with the whole table, not with the
--                                account being read, so the without-index plan degrades on every
--                                account as the table grows.
--
--   idx_user_strikes_active      most recent active strike, and the active strike count
--                                without: Seq Scan, 4,000 rows discarded, 54 buffers
--                                         latest 0.34 ms, count 0.33 ms
--                                with:    Index Scan and Index Only Scan, 2 buffers
--                                         latest 0.07 ms, count 0.04 ms
--                                The absolute saving is small today because user_strikes is small.
--                                The plan change is not: both statements run on every warning, so
--                                the scan is on the write path of the most frequent action here.
--
--   uq_user_strikes_active_number  correctness guard, kept regardless of any plan
--                                Two concurrent warnings can both read an active count of two and
--                                both try to issue the same strike number. This index is what
--                                rejects the second; the application check cannot close that race.
--                                Partial on revoked_at IS NULL so revoking a strike frees its
--                                number, which is what lets an administrator re-issue one.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_warnings_active
    ON user_warnings (user_id, created_at DESC) WHERE revoked_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_strikes_active
    ON user_strikes (user_id, created_at DESC) WHERE revoked_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_user_strikes_active_number
    ON user_strikes (user_id, strike_number) WHERE revoked_at IS NULL;
