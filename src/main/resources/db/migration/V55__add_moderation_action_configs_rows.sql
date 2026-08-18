-- Registers display metadata for the twelve admin_action_type values added by V54.
--
-- Separate from V54 because these INSERTs name the new enum values, which PostgreSQL does not make
-- usable until the ALTER TYPE transaction has committed.
--
-- requires_reason is TRUE for every action that removes or reduces a capability, and FALSE for the
-- reversal of one, matching the pattern V18 established for the original ten rows.
-- is_reversible is FALSE where the action has no inverse action_type in the enum.

INSERT INTO moderation_action_configs (action_key, display_name, requires_reason, is_reversible, is_enabled) VALUES
    ('change_user_role', 'Change User Role', TRUE,  TRUE,  TRUE),
    ('warn_user',        'Warn User',        TRUE,  TRUE,  TRUE),
    ('revoke_warning',   'Revoke Warning',   FALSE, FALSE, TRUE),
    ('issue_strike',     'Issue Strike',     TRUE,  TRUE,  TRUE),
    ('revoke_strike',    'Revoke Strike',    FALSE, FALSE, TRUE),
    ('escalate_report',  'Escalate Report',  TRUE,  FALSE, TRUE),
    ('force_logout',     'Force Logout',     TRUE,  FALSE, TRUE),
    ('create_hashtag',   'Create Hashtag',   FALSE, TRUE,  TRUE),
    ('edit_hashtag',     'Edit Hashtag',     TRUE,  TRUE,  TRUE),
    ('ban_hashtag',      'Ban Hashtag',      TRUE,  TRUE,  TRUE),
    ('unban_hashtag',    'Unban Hashtag',    FALSE, TRUE,  TRUE),
    ('delete_hashtag',   'Delete Hashtag',   TRUE,  FALSE, TRUE)
ON CONFLICT (action_key) DO NOTHING;
