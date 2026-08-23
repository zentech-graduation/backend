-- Registers the reporter-facing notification for successful post report outcomes.

INSERT INTO notification_type_configs (type_key, display_name, template_key, is_user_toggleable, is_enabled) VALUES
    ('report_post_removed', 'Reported Post Removed', 'report_post_removed', FALSE, TRUE)
ON CONFLICT (type_key) DO NOTHING;
