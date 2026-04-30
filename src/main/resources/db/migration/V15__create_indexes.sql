-- Flyway migration V15
-- Source: database/schema.sql lines 585-688
-- All indexes across the schema. Partial indexes preserve their predicates;
-- GIN indexes back fuzzy username search and full-text search.

-- users
CREATE INDEX idx_users_username         ON users USING btree (username) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_email            ON users USING btree (email);
CREATE INDEX idx_users_status           ON users USING btree (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_created_at       ON users USING btree (created_at DESC);
CREATE INDEX idx_users_username_trgm    ON users USING gin (username gin_trgm_ops);
CREATE INDEX idx_users_fts              ON users USING gin (
    to_tsvector('simple', COALESCE(username, '') || ' ' || COALESCE(display_name, ''))
);

-- follows
CREATE INDEX idx_follows_following      ON follows (following_id, status, created_at DESC);
CREATE INDEX idx_follows_follower       ON follows (follower_id, status, created_at DESC);

-- blocks
CREATE INDEX idx_blocks_blocker         ON blocks (blocker_id);
CREATE INDEX idx_blocks_blocked         ON blocks (blocked_id);

-- media_assets
CREATE INDEX idx_media_assets_user      ON media_assets (user_id, created_at DESC);

-- posts
CREATE INDEX idx_posts_user_feed        ON posts (user_id, created_at DESC)
    WHERE status = 'published' AND deleted_at IS NULL;
CREATE INDEX idx_posts_created_at       ON posts (created_at DESC)
    WHERE status = 'published' AND deleted_at IS NULL;
CREATE INDEX idx_posts_status           ON posts (status) WHERE deleted_at IS NULL;

-- post_media
CREATE INDEX idx_post_media_post        ON post_media (post_id, position);

-- post_likes
CREATE INDEX idx_post_likes_post        ON post_likes (post_id, created_at DESC);
CREATE INDEX idx_post_likes_user        ON post_likes (user_id, created_at DESC);

-- post_saves
CREATE INDEX idx_post_saves_user        ON post_saves (user_id, created_at DESC);

-- comments
CREATE INDEX idx_comments_post_root     ON comments (post_id, created_at ASC)
    WHERE parent_id IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_comments_parent        ON comments (parent_id, created_at ASC)
    WHERE parent_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_comments_root          ON comments (root_id)
    WHERE root_id IS NOT NULL;
CREATE INDEX idx_comments_user          ON comments (user_id);

-- comment_likes
CREATE INDEX idx_comment_likes_comment  ON comment_likes (comment_id);
CREATE INDEX idx_comment_likes_user     ON comment_likes (user_id);

-- hashtags
CREATE INDEX idx_hashtags_name          ON hashtags USING btree (name);
CREATE INDEX idx_hashtags_name_trgm     ON hashtags USING gin (name gin_trgm_ops);
CREATE INDEX idx_hashtags_post_count    ON hashtags (post_count DESC);
CREATE INDEX idx_post_hashtags_tag      ON post_hashtags (hashtag_id, post_id);

-- stories
CREATE INDEX idx_stories_user           ON stories (user_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_stories_expires        ON stories (expires_at)
    WHERE deleted_at IS NULL;

-- notifications
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread    ON notifications (recipient_id, created_at DESC)
    WHERE is_read = FALSE;

-- conversations
CREATE INDEX idx_conversations_updated  ON conversations (last_message_at DESC NULLS LAST);

-- conversation_participants
CREATE INDEX idx_conv_part_user         ON conversation_participants (user_id, last_read_at DESC)
    WHERE left_at IS NULL;

-- messages
CREATE INDEX idx_messages_conversation  ON messages (conversation_id, created_at DESC)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_messages_sender        ON messages (sender_id);

-- reports
CREATE INDEX idx_reports_status         ON reports (status, created_at DESC);
CREATE INDEX idx_reports_entity         ON reports (entity_id, report_type);
CREATE INDEX idx_reports_reporter       ON reports (reporter_id);

-- admin_actions
CREATE INDEX idx_admin_actions_admin    ON admin_actions (admin_id, created_at DESC);
CREATE INDEX idx_admin_actions_target   ON admin_actions (target_user_id)
    WHERE target_user_id IS NOT NULL;

-- refresh_tokens
CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL;

-- user_events (cascades to all partitions)
CREATE INDEX idx_user_events_user       ON user_events (user_id, created_at DESC);
CREATE INDEX idx_user_events_type       ON user_events (event_type, created_at DESC);
CREATE INDEX idx_user_events_entity     ON user_events (entity_type, entity_id, created_at DESC)
    WHERE entity_id IS NOT NULL;

-- post_interaction_scores
CREATE INDEX idx_post_scores_total      ON post_interaction_scores (total_score DESC);

-- user_similarity
CREATE INDEX idx_user_sim_score         ON user_similarity (user_id_a, similarity_score DESC);
