-- Flyway migration V49
-- Story likes: mirrors post_likes (composite PK, trigger-maintained counter on the parent row).

ALTER TABLE stories ADD COLUMN like_count INT NOT NULL DEFAULT 0 CHECK (like_count >= 0);

CREATE TABLE story_likes (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id            UUID            NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, story_id)
);

CREATE OR REPLACE FUNCTION fn_story_like_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE stories SET like_count = like_count + 1 WHERE id = NEW.story_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE stories SET like_count = GREATEST(like_count - 1, 0) WHERE id = OLD.story_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_story_like_count
    AFTER INSERT OR DELETE ON story_likes
    FOR EACH ROW EXECUTE FUNCTION fn_story_like_count();
