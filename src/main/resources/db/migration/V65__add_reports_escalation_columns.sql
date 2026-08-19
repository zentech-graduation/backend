-- Records who escalated a report, when, and why.
--
-- Kept as columns on reports rather than derived from admin_actions, for the same reason the
-- pre-removal post status is: the audit log records what happened and must not become load-bearing
-- for application behaviour. The escalation reason is shown to the administrator who picks the
-- report up, on a read path that should not have to join the audit table to work.
--
-- escalated_by is ON DELETE SET NULL, matching reviewed_by: deleting the moderator's account must
-- not delete the report it escalated.
--
-- All three are nullable with no default, so this is a catalogue-only change on an existing table.
-- The partial index that names the new status value is deliberately deferred to V66, which runs
-- outside a transaction so the build can be CONCURRENTLY.

ALTER TABLE reports ADD COLUMN escalated_by      UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE reports ADD COLUMN escalated_at      TIMESTAMPTZ;
ALTER TABLE reports ADD COLUMN escalation_reason TEXT;
