-- Registers moderation notification types for restored posts and dismissed reports.

INSERT INTO notification_type_configs (type_key, display_name, template_key, is_user_toggleable, is_enabled) VALUES
    ('post_restored', 'Post Restored', 'post_restored', FALSE, TRUE),
    ('report_dismissed', 'Report Dismissed', 'report_dismissed', FALSE, TRUE)
ON CONFLICT (type_key) DO NOTHING;
