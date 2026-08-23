-- Adds the audit type for revoking one named session.
--
-- Not folded into force_logout, which ends every session an account holds. An audit row saying
-- force_logout for a single-session revocation would assert something that did not happen, which
-- is the same defect the story and message actions were added to close.
--
-- This file contains nothing but ALTER TYPE. The moderation_action_configs row naming the value
-- lives in V80, because PostgreSQL does not make a new enum value usable until the adding
-- transaction has committed.

ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'revoke_session';
