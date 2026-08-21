-- Adds the report status a moderator uses to hand a decision up to an administrator.
--
-- Without it a moderator facing a report it should not decide alone had two options: close it
-- anyway, or leave it pending and hope. Escalation makes the third option explicit and auditable.
--
-- This file contains nothing but the ALTER TYPE. PostgreSQL does not make a new enum value usable
-- until the transaction that added it has committed, so the columns and the partial index that
-- names the value live in V65 and V66.

ALTER TYPE report_status ADD VALUE IF NOT EXISTS 'escalated';
