-- Flyway migration V25
-- Comment module: add moderation_status to comments and a partial index for the public read path.
-- moderation_status is a VARCHAR (not a PG enum) so adding states needs no enum migration.

ALTER TABLE comments
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'approved';

-- Serves the public read path: top-level approved comments for a post, newest first.
CREATE INDEX idx_comments_post_moderation
    ON comments (post_id, moderation_status, created_at DESC)
    WHERE deleted_at IS NULL;
