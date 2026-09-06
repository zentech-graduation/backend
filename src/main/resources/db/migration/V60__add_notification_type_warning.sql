-- Adds the notification type a moderation warning is delivered as.
--
-- V54 extended admin_action_type with warn_user and the rest of the discipline actions but did not
-- touch notification_type, which had no value for a message sent by the platform rather than by
-- another account. A warning that never reaches the account it is about cannot change behaviour,
-- which is the only reason to issue one.
--
-- This file contains nothing but the ALTER TYPE. PostgreSQL does not make a new enum value usable
-- until the transaction that added it has committed, so the notification_type_configs row that
-- names it lives in V61.

ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'warning';
