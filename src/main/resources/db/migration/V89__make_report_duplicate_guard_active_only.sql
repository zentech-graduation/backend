-- Allow a viewer to report the same target again after the previous report has been closed.
--
-- Active reports still have one-row-per (reporter, type, target) protection; terminal reports are
-- retained for audit/history but no longer block a fresh report after content is restored or a new
-- issue appears.

DROP INDEX IF EXISTS uq_reports_reporter_type_entity;

CREATE UNIQUE INDEX uq_reports_reporter_type_entity
    ON reports (reporter_id, report_type, entity_id)
    WHERE status IN ('pending', 'reviewing', 'escalated');
