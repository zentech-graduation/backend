-- Flyway migration V43
-- Align the case-insensitive username constraint with the documented soft-delete policy.
--
-- GLOBAL_RULES.md section 3 states that soft delete does NOT release a username, that the
-- uniqueness constraint remains enforced regardless of deleted_at, that release happens only on
-- hard delete, and that no purge job exists.
--
-- V42 left three components disagreeing about scope:
--   idx_users_username_lower      UNIQUE on lower(username) WHERE deleted_at IS NULL  -- active only
--   V42 collision guard           counted all rows, including soft-deleted
--   existsByUsername              table-wide, served by idx_users_username_lower_all
--
-- The partial index was the odd one out: it permitted a live account to take a username still
-- held by a soft-deleted one, which the policy forbids. No application path could reach that
-- state, because existsByUsername guards all three write paths (registration, OAuth username
-- generation, profile update) and the application never writes users.deleted_at at all, so the
-- inconsistency was latent rather than exploitable. It is closed here so the database enforces
-- the same rule the service does.
--
-- The two functional indexes on lower(username) collapse into one. Measured against 200,000 rows
-- with 30% soft-deleted, the planner never chose the partial index even for queries whose
-- predicate matched it; the non-partial index answered every shape at identical buffer counts.
-- Collapsing removes 4328 kB of duplicated index for no loss:
--
--   two indexes:  login 0.041 ms / exists 0.027 ms / 10496 kB total
--   one index:    login 0.051 ms / exists 0.024 ms /  6168 kB total
--
-- users_username_key (V02), the plain UNIQUE on the raw column, is retained as a structural
-- guard. It is now implied by this index but is not dropped here: index removal requires
-- production idx_scan evidence, which this migration does not have.

-- Abort if two accounts already collide once lowercased, counting soft-deleted rows because the
-- new index does. Same shape and same fail-closed posture as the V42 guard. Auto-resolution is
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

DROP INDEX IF EXISTS idx_users_username_lower;
DROP INDEX IF EXISTS idx_users_username_lower_all;

CREATE UNIQUE INDEX idx_users_username_lower
  ON users (lower(username));
