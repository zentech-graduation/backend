-- Flyway migration V16
-- Source: database/schema.sql lines 695-920
-- 11 trigger functions and 11 triggers maintaining denormalized counters and updated_at.

CREATE OR REPLACE FUNCTION fn_update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE OR REPLACE FUNCTION fn_follow_counts()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'accepted' THEN
        UPDATE users SET following_count = following_count + 1 WHERE id = NEW.follower_id;
        UPDATE users SET follower_count  = follower_count  + 1 WHERE id = NEW.following_id;

    ELSIF TG_OP = 'DELETE' AND OLD.status = 'accepted' THEN
        UPDATE users SET following_count = GREATEST(following_count - 1, 0) WHERE id = OLD.follower_id;
        UPDATE users SET follower_count  = GREATEST(follower_count  - 1, 0) WHERE id = OLD.following_id;

    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.status <> 'accepted' AND NEW.status = 'accepted' THEN
            UPDATE users SET following_count = following_count + 1 WHERE id = NEW.follower_id;
            UPDATE users SET follower_count  = follower_count  + 1 WHERE id = NEW.following_id;
        ELSIF OLD.status = 'accepted' AND NEW.status <> 'accepted' THEN
            UPDATE users SET following_count = GREATEST(following_count - 1, 0) WHERE id = NEW.follower_id;
            UPDATE users SET follower_count  = GREATEST(follower_count  - 1, 0) WHERE id = NEW.following_id;
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_follow_counts
    AFTER INSERT OR UPDATE OR DELETE ON follows
    FOR EACH ROW EXECUTE FUNCTION fn_follow_counts();

CREATE OR REPLACE FUNCTION fn_post_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'published' AND NEW.deleted_at IS NULL THEN
        UPDATE users SET post_count = post_count + 1 WHERE id = NEW.user_id;

    ELSIF TG_OP = 'DELETE' AND OLD.status = 'published' AND OLD.deleted_at IS NULL THEN
        UPDATE users SET post_count = GREATEST(post_count - 1, 0) WHERE id = OLD.user_id;

    ELSIF TG_OP = 'UPDATE' THEN
        IF (OLD.status <> 'published' OR OLD.deleted_at IS NOT NULL)
            AND NEW.status = 'published' AND NEW.deleted_at IS NULL THEN
            UPDATE users SET post_count = post_count + 1 WHERE id = NEW.user_id;
        ELSIF OLD.status = 'published' AND OLD.deleted_at IS NULL
            AND (NEW.status <> 'published' OR NEW.deleted_at IS NOT NULL) THEN
            UPDATE users SET post_count = GREATEST(post_count - 1, 0) WHERE id = NEW.user_id;
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_post_count
    AFTER INSERT OR UPDATE OR DELETE ON posts
    FOR EACH ROW EXECUTE FUNCTION fn_post_count();

CREATE OR REPLACE FUNCTION fn_post_like_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE posts SET like_count = like_count + 1 WHERE id = NEW.post_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = OLD.post_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_post_like_count
    AFTER INSERT OR DELETE ON post_likes
    FOR EACH ROW EXECUTE FUNCTION fn_post_like_count();

CREATE OR REPLACE FUNCTION fn_post_save_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE posts SET save_count = save_count + 1 WHERE id = NEW.post_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE posts SET save_count = GREATEST(save_count - 1, 0) WHERE id = OLD.post_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_post_save_count
    AFTER INSERT OR DELETE ON post_saves
    FOR EACH ROW EXECUTE FUNCTION fn_post_save_count();

CREATE OR REPLACE FUNCTION fn_post_comment_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.deleted_at IS NULL THEN
        UPDATE posts SET comment_count = comment_count + 1 WHERE id = NEW.post_id;
    ELSIF TG_OP = 'DELETE' AND OLD.deleted_at IS NULL THEN
        UPDATE posts SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = OLD.post_id;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            UPDATE posts SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = NEW.post_id;
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            UPDATE posts SET comment_count = comment_count + 1 WHERE id = NEW.post_id;
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_post_comment_count
    AFTER INSERT OR UPDATE OR DELETE ON comments
    FOR EACH ROW EXECUTE FUNCTION fn_post_comment_count();

CREATE OR REPLACE FUNCTION fn_comment_like_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE comments SET like_count = like_count + 1 WHERE id = NEW.comment_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE comments SET like_count = GREATEST(like_count - 1, 0) WHERE id = OLD.comment_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_comment_like_count
    AFTER INSERT OR DELETE ON comment_likes
    FOR EACH ROW EXECUTE FUNCTION fn_comment_like_count();

CREATE OR REPLACE FUNCTION fn_comment_reply_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.parent_id IS NOT NULL AND NEW.deleted_at IS NULL THEN
        UPDATE comments SET reply_count = reply_count + 1 WHERE id = NEW.parent_id;
    ELSIF TG_OP = 'DELETE' AND OLD.parent_id IS NOT NULL AND OLD.deleted_at IS NULL THEN
        UPDATE comments SET reply_count = GREATEST(reply_count - 1, 0) WHERE id = OLD.parent_id;
    ELSIF TG_OP = 'UPDATE' AND NEW.parent_id IS NOT NULL THEN
        IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
            UPDATE comments SET reply_count = GREATEST(reply_count - 1, 0) WHERE id = NEW.parent_id;
        ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
            UPDATE comments SET reply_count = reply_count + 1 WHERE id = NEW.parent_id;
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_comment_reply_count
    AFTER INSERT OR UPDATE OR DELETE ON comments
    FOR EACH ROW EXECUTE FUNCTION fn_comment_reply_count();

CREATE OR REPLACE FUNCTION fn_hashtag_post_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE hashtags SET post_count = post_count + 1 WHERE id = NEW.hashtag_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE hashtags SET post_count = GREATEST(post_count - 1, 0) WHERE id = OLD.hashtag_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hashtag_post_count
    AFTER INSERT OR DELETE ON post_hashtags
    FOR EACH ROW EXECUTE FUNCTION fn_hashtag_post_count();

CREATE OR REPLACE FUNCTION fn_story_view_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE stories SET view_count = view_count + 1 WHERE id = NEW.story_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE stories SET view_count = GREATEST(view_count - 1, 0) WHERE id = OLD.story_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_story_view_count
    AFTER INSERT OR DELETE ON story_views
    FOR EACH ROW EXECUTE FUNCTION fn_story_view_count();

CREATE OR REPLACE FUNCTION fn_conversation_last_message()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE conversations
    SET last_message_at = NEW.created_at,
        updated_at      = NEW.created_at
    WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_conversation_last_message
    AFTER INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION fn_conversation_last_message();
