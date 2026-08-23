-- Registers display metadata for the four admin_action_type values added by V75.
--
-- Separate from V75 because these INSERTs name the new enum values, which PostgreSQL does not make
-- usable until the ALTER TYPE transaction has committed.
--
-- requires_reason and is_reversible follow the pattern V18 established and V55 continued: a reason
-- is demanded for the action that takes content down and not for the one that puts it back, and
-- both directions are reversible because each has an inverse action_type in the enum.

INSERT INTO moderation_action_configs (action_key, display_name, requires_reason, is_reversible, is_enabled) VALUES
    ('remove_story',    'Remove Story',    TRUE,  TRUE, TRUE),
    ('restore_story',   'Restore Story',   FALSE, TRUE, TRUE),
    ('remove_message',  'Remove Message',  TRUE,  TRUE, TRUE),
    ('restore_message', 'Restore Message', FALSE, TRUE, TRUE)
ON CONFLICT (action_key) DO NOTHING;
