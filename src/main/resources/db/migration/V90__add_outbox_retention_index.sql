-- Supports the retention job's candidate scan over published outbox rows.
--
-- Runs outside a transaction, declared in the accompanying V90__*.sql.conf, so the build can be
-- CONCURRENTLY. outbox_events is on the write path of every domain event, so a plain CREATE INDEX
-- holding a write lock for the whole build is an availability event.
--
-- The existing idx_outbox_events_publish_scan is partial on status IN ('PENDING','PROCESSING') and
-- therefore excludes every row the retention job targets. Without this index the batch delete
-- degrades to a sequential scan on exactly the table whose unbounded growth the job exists to stop,
-- so the job would get slower in proportion to the problem it is solving.
--
-- Partial on status = 'PUBLISHED' rather than a plain index on published_at: the table's own CHECK
-- constraint guarantees published_at IS NOT NULL if and only if status = 'PUBLISHED', so the
-- partial form indexes the same rows in less space and lets the planner drop the status recheck.
--
-- DEAD, PENDING and PROCESSING rows are deliberately outside this index. They are never eligible
-- for deletion, so indexing them would cost write amplification for candidates that can never match.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_outbox_events_retention
    ON outbox_events (published_at)
    WHERE status = 'PUBLISHED';
