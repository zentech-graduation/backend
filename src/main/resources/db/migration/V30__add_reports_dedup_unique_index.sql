-- Flyway migration V30
-- Enforce one report per (reporter, target) at the database layer so the application-level
-- duplicate check cannot be bypassed by concurrent submissions.

-- Collapse any pre-existing duplicates first, keeping the earliest report per group, so the
-- unique index can be created on a database that already holds duplicate rows.
DELETE FROM reports r
USING reports keep
WHERE r.reporter_id = keep.reporter_id
  AND r.report_type = keep.report_type
  AND r.entity_id = keep.entity_id
  AND (r.created_at > keep.created_at
       OR (r.created_at = keep.created_at AND r.id > keep.id));

CREATE UNIQUE INDEX uq_reports_reporter_entity
    ON reports (reporter_id, report_type, entity_id);
