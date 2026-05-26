-- Flyway migration V17
-- Source: database/schema.sql lines 925-950
-- Convenience views: active_stories, pending_follow_requests, pending_reports.

CREATE OR REPLACE VIEW active_stories AS
SELECT s.*, u.username, u.avatar_url
FROM stories s
JOIN users u ON u.id = s.user_id AND u.deleted_at IS NULL
WHERE s.deleted_at IS NULL
  AND s.expires_at > NOW();

CREATE OR REPLACE VIEW pending_follow_requests AS
SELECT f.*,
       follower.username   AS follower_username,
       follower.avatar_url AS follower_avatar
FROM follows f
JOIN users follower ON follower.id = f.follower_id AND follower.deleted_at IS NULL
WHERE f.status = 'pending';

CREATE OR REPLACE VIEW pending_reports AS
SELECT r.*,
       reporter.username AS reporter_username
FROM reports r
JOIN users reporter ON reporter.id = r.reporter_id AND reporter.deleted_at IS NULL
WHERE r.status = 'pending'
ORDER BY r.created_at ASC;
