-- The index the time series read needs, and the primary key cannot serve.
--
-- Runs outside a transaction, declared in the accompanying V71__*.sql.conf, so the build can be
-- CONCURRENTLY. platform_stats is created empty by V70, so the availability argument that makes
-- CONCURRENTLY mandatory does not bite here; the statement is written that way anyway because a
-- rule with a case-by-case exemption stops being checkable, and the cost on an empty table is nil.
--
-- The time series query is
--   SELECT ... FROM platform_stats
--    WHERE metric_key = ? AND granularity = ? AND bucket_start >= ? AND bucket_start < ?
--    ORDER BY bucket_start
-- and the primary key leads with bucket_start, so it can bound the range but cannot narrow to one
-- metric without filtering every metric written in that range. At 14 metric families times 48
-- buckets a day, a 30-day window is roughly 20,000 rows read to return 1,440. This index leads with
-- the two equality columns and carries the range and the ordering, so the same query is one index
-- scan reading only the rows it returns, in order, with no sort.
--
-- The current-snapshot read is deliberately not a reason for this index. It resolves
--   bucket_start = (SELECT max(bucket_start) ...)
-- which the primary key already serves as a backwards index scan for the subquery and an equality
-- lookup for the outer read.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_platform_stats_series
    ON platform_stats (metric_key, granularity, bucket_start DESC);
