-- Registers display metadata for the admin_action_type value added by V79.
--
-- requires_reason is TRUE because ending someone's session takes a capability away, matching the
-- pattern V18 established. is_reversible is FALSE: a revoked session cannot be un-revoked, and the
-- account simply signs in again.

INSERT INTO moderation_action_configs (action_key, display_name, requires_reason, is_reversible, is_enabled) VALUES
    ('revoke_session', 'Revoke Session', TRUE, FALSE, TRUE)
ON CONFLICT (action_key) DO NOTHING;
