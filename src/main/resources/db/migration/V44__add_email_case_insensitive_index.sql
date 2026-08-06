-- Flyway migration V44
-- Enforce case-insensitive email uniqueness, matching the username treatment (V42/V43).
--
-- GLOBAL_RULES.md section 3 states the same soft-delete-retention policy for username and email:
-- neither is released by a soft delete, and the UNIQUE constraint is enforced regardless of
-- deleted_at. Username's case-insensitive index went straight to table-wide, non-partial in V43
-- after V42's partial index proved to be the wrong scope; this migration applies that same
-- table-wide shape directly rather than repeating the two-step detour.
--
-- Email's real-world semantics are case-insensitive in both RFC 5321 (the domain part) and every
-- major mail provider's practice (the local part too), so `Alice@example.com` and
-- `alice@example.com` must resolve to one account, the same as `Alice` and `alice` do for
-- username. Before this migration, `users_email_key` (V02) is a plain case-sensitive UNIQUE, so
-- the two could coexist as separate accounts on one mailbox.

-- Abort if two accounts already collide once lowercased, counting soft-deleted rows since the
-- new index does. Same fail-closed posture as the V42/V43 username guards: auto-resolution is
-- deliberately not attempted, colliding accounts must be reconciled by an operator first.
DO $$
DECLARE collision_count INTEGER;
BEGIN
  SELECT COUNT(*) INTO collision_count
  FROM (
    SELECT lower(email)
    FROM users
    GROUP BY lower(email)
    HAVING COUNT(*) > 1
  ) dupes;

  IF collision_count > 0 THEN
    RAISE EXCEPTION
      'Email case collision detected: % normalized emails map to multiple accounts. '
      'Resolve collisions before applying this migration.', collision_count;
  END IF;
END $$;

CREATE UNIQUE INDEX idx_users_email_lower
  ON users (lower(email));

-- users_email_key (V02), the plain UNIQUE on the raw column, is retained as a structural guard.
-- It is now implied by this index but is not dropped here, consistent with users_username_key's
-- treatment in V43: index removal requires production idx_scan evidence, which this migration
-- does not have.
