-- Adds the notification type used when moderation removes a post.
--
-- This file contains only the enum extension. PostgreSQL does not make a new enum value usable
-- inside the same transaction that added it, so the notification_type_configs row lives in V84.

ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'post_removed';
