-- Lets system notifications carry a short explanatory message, and registers the post-removal
-- notification type added by V83.
--
-- The first user of message is moderation post removal, where the administrator's recorded reason
-- must be visible to the post owner.

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS message TEXT;

INSERT INTO notification_type_configs (type_key, display_name, template_key, is_user_toggleable, is_enabled) VALUES
    ('post_removed', 'Post Removed', 'post_removed', FALSE, TRUE)
ON CONFLICT (type_key) DO NOTHING;
