-- Flyway migration V51
-- Removes a deadlock between two people following each other back at the same instant.
--
-- fn_follow_counts updates two users rows per follow, in the order (follower, following). Two
-- concurrent inserts in opposite directions therefore take the same two row locks in opposite
-- order, which Postgres resolves by killing one transaction:
--
--   alice -> bob   locks alice, then bob
--   bob   -> alice locks bob,   then alice
--
-- The loser sees "deadlock detected" and the follow fails with a 500. This has been reachable
-- since the schema was created, but only became likely once mutual follows started provisioning
-- conversations, because following back is now the interaction the product encourages.
--
-- The fix is to acquire both row locks up front in a deterministic order, so the two directions of
-- a pair queue behind each other instead of colliding.
--
-- The lock strength matters as much as the order. Inserting into follows makes its two foreign
-- keys take FOR KEY SHARE on both users rows before this trigger runs, and both transactions hold
-- those at once because key-share locks do not conflict with each other. FOR UPDATE would conflict
-- with them, so each transaction would wait on the other's key-share lock over the same row and
-- deadlock again, ordering notwithstanding. FOR NO KEY UPDATE is the strength a plain UPDATE of a
-- non-key column already takes: it excludes the other writer while remaining compatible with the
-- foreign keys' key-share locks.
--
-- The counters themselves are unchanged: this alters lock acquisition only.

CREATE OR REPLACE FUNCTION fn_follow_counts()
RETURNS TRIGGER AS $$
DECLARE
    follower_key  UUID;
    following_key UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        follower_key  := OLD.follower_id;
        following_key := OLD.following_id;
    ELSE
        follower_key  := NEW.follower_id;
        following_key := NEW.following_id;
    END IF;

    -- Both rows locked in id order before either is written, so the two directions of a pair can
    -- never hold one lock each and wait on the other.
    PERFORM 1 FROM users
    WHERE id IN (follower_key, following_key)
    ORDER BY id
    FOR NO KEY UPDATE;

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
