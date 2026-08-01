-- Flyway migration V42
-- Enforce case-insensitive username uniqueness for active (non-deleted) users.
-- The raw UNIQUE constraint on users.username (V02) is retained as a structural guard;
-- this migration adds a functional unique index on lower(username) that the username
-- lookups compare against.
--
-- Stored casing is deliberately left untouched. Identity is case-insensitive, display is
-- case-preserving: the functional index enforces "one account per lowercased username"
-- without requiring the raw column to be lowercased, and users.username is what the public
-- profile renders. Lowercasing the column here would be a one-way door, because no column
-- retains the original casing.

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

CREATE UNIQUE INDEX idx_users_username_lower
  ON users (lower(username))
  WHERE deleted_at IS NULL;

-- Registration and OAuth username generation check availability table-wide, because soft delete
-- does not release a username and no purge job exists. The partial index above cannot answer a
-- query that spans soft-deleted rows, so without this one every availability check degrades to a
-- sequential scan of users. Non-unique deliberately: a table-wide unique index on lower(username)
-- would be stricter than the existing users_username_key and could fail on data that is currently
-- legal.
CREATE INDEX idx_users_username_lower_all
  ON users (lower(username));
