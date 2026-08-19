-- One partial index serving both readers of the escalated queue.
--
-- Runs outside a transaction, declared in the accompanying V66__*.sql.conf, so the build can be
-- CONCURRENTLY. reports is on the write path of every content flag a user submits.
--
-- The administrator queue reads escalated reports oldest first, which is the same FIFO ordering the
-- pending_reports view gives the moderator queue, and the counter endpoint counts the same rows.
-- A partial index on the status carries the ordering columns as well, so the queue is an index scan
-- and the count is an index-only scan over the same structure. Ordering ASC matches the query;
-- PostgreSQL can read either direction, but stating it keeps the index and the statement legible
-- as one thing.
--
-- Measured on the live schema rather than a synthetic table: reports is small in this system, so
-- the honest justification is not a timing delta but that the alternative is a sequential scan
-- whose cost grows with the total number of reports ever filed, while the escalated subset stays
-- small by construction. An escalated report is one an administrator is expected to clear.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reports_escalated
    ON reports (created_at ASC, id ASC) WHERE status = 'escalated';
