-- Enforce single-report-per-target at the database level. Duplicate suppression previously
-- existed only as an application-level check-then-act, which two concurrent submissions could
-- both pass before either row was inserted.

-- Remove pre-existing duplicates before the unique index is created, otherwise the index build
-- fails on any database that already contains them. The earliest report per
-- (reporter_id, report_type, entity_id) is retained; ties on created_at are broken by id.
DELETE FROM reports r
USING reports keep
WHERE r.reporter_id = keep.reporter_id
  AND r.report_type = keep.report_type
  AND r.entity_id = keep.entity_id
  AND (keep.created_at < r.created_at
       OR (keep.created_at = r.created_at AND keep.id < r.id));

CREATE UNIQUE INDEX uq_reports_reporter_type_entity
    ON reports (reporter_id, report_type, entity_id);
