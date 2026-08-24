-- Adds the notification type sent to a reporter when a reported post is removed.
--
-- Keep enum extension separate from any migration that needs to use the value.

ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'report_post_removed';
