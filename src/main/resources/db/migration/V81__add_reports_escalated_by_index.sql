-- Serves a moderator's list of the reports it escalated.
--
-- Runs outside a transaction, declared in the accompanying .sql.conf, so the build is
-- CONCURRENTLY. reports is on the write path of every content flag a user submits.
--
-- Distinct from idx_reports_escalated (V66), which is partial on status = 'escalated' and orders
-- by created_at. This listing is keyed on who escalated, ordered by when they did it, and must
-- keep showing a report after an administrator has resolved it, so the status predicate would
-- exclude exactly the rows the moderator is following up. Partial on escalated_by IS NOT NULL
-- because the overwhelming majority of reports were never escalated.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reports_escalated_by
    ON reports (escalated_by, escalated_at DESC, id DESC)
    WHERE escalated_by IS NOT NULL;
