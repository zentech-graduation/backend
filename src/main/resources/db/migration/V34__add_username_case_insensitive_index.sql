-- Flyway migration V34
-- Enforce case-insensitive username uniqueness for active (non-deleted) users.
-- The raw UNIQUE constraint on users.username (V02) is retained as a structural guard;
-- this migration normalizes existing usernames to lowercase and adds a functional unique
-- index on lower(username) that the username login lookup relies on.

-- Abort if normalizing to lowercase would merge two distinct accounts. Auto-resolution is
-- deliberately not attempted: colliding accounts must be reconciled by an operator first.
DO $$
DECLARE collision_count INTEGER;
BEGIN
  SELECT COUNT(*) INTO collision_count
  FROM (
    SELECT lower(username)
    FROM users
    GROUP BY lower(username)
    HAVING COUNT(*) > 1
  ) dupes;

  IF collision_count > 0 THEN
    RAISE EXCEPTION
      'Username case collision detected: % normalized usernames map to multiple accounts. '
      'Resolve collisions before applying this migration.', collision_count;
  END IF;
END $$;

UPDATE users SET username = lower(username);

CREATE UNIQUE INDEX idx_users_username_lower
  ON users (lower(username))
  WHERE deleted_at IS NULL;
