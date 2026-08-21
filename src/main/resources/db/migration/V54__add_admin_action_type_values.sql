-- Extends admin_action_type with every moderation action the admin surface is planned to record.
--
-- Only change_user_role and force_logout have a caller in this release. The other ten are added
-- now on purpose and are not dead weight: ALTER TYPE ... ADD VALUE cannot be followed in the same
-- migration by a statement that uses the new value, so each batch of additions costs its own
-- migration file. Adding all twelve here collapses three future migrations into this one.
--
-- This file contains nothing but ALTER TYPE statements for that same reason. The
-- moderation_action_configs rows that name these values live in V55, which runs in a later
-- transaction where the values are usable.

ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'change_user_role';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'warn_user';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'revoke_warning';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'issue_strike';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'revoke_strike';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'escalate_report';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'force_logout';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'create_hashtag';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'edit_hashtag';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'ban_hashtag';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'unban_hashtag';
ALTER TYPE admin_action_type ADD VALUE IF NOT EXISTS 'delete_hashtag';
