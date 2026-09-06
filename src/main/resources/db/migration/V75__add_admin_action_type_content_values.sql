-- Extends admin_action_type with the story and message moderation actions.
--
-- report_type has five values and only two of them had a moderation path. A moderator closing a
-- report about a story or a message recorded a decision in admin_actions while the content stayed
-- up, so the audit log asserted a removal that had not happened and nothing inside the system
-- could detect the divergence.
--
-- This file contains nothing but ALTER TYPE statements. PostgreSQL does not make a new enum value
-- usable until the adding transaction has committed, so the moderation_action_configs rows that
-- name these four values live in V76.

ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'remove_story';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'restore_story';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'remove_message';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'restore_message';
