-- Registers display metadata for the notification_type value added by V60.
--
-- Separate from V60 because this INSERT names the new enum value, which PostgreSQL does not make
-- usable until the ALTER TYPE transaction has committed.
--
-- is_user_toggleable is FALSE, unlike every row V18 seeded. A warning is a moderation
-- communication, not social activity: an account that could switch it off would be disciplined
-- without ever being told, and the strike that follows three warnings would arrive unexplained.
-- NotificationServiceImpl.isTypeEnabled has no user_settings column to consult for this type and
-- returns true unconditionally, so this row records that fact rather than establishing it.

INSERT INTO notification_type_configs (type_key, display_name, template_key, is_user_toggleable, is_enabled) VALUES
    ('warning', 'Moderation Warning', 'warning', FALSE, TRUE)
ON CONFLICT (type_key) DO NOTHING;
