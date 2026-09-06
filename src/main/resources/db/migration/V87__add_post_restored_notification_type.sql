-- Adds moderation notification types for restored posts and dismissed reports.
--
-- Keep enum extension separate from any migration that needs to use the values.

ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'post_restored';
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'report_dismissed';
