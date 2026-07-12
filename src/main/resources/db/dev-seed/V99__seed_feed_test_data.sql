-- =============================================================================
-- V99__seed_feed_test_data.sql   DEV SEED — loaded only in the `dev` profile.
--
-- This file lives in classpath:db/dev-seed, which is added to spring.flyway.locations
-- ONLY by application-dev.yml. Production (application.yaml / application-prod.yml) scans
-- classpath:db/migration only, so this seed is never applied outside development.
--
-- Defense-in-depth: the DML below is additionally wrapped in an environment guard so it is a
-- no-op if the database session declares itself production via the `app.environment` GUC.
--
-- Password for ALL seeded accounts: SeedTest@123
-- Hash: BCryptPasswordEncoder(cost=12).encode("SeedTest@123")
--       $2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG
--
-- =============================================================================
-- ROWS INSERTED AND THE FEED RULE EACH EXERCISES
-- =============================================================================
--
-- USERS (7) — all status=active, email_verified=true so login works
--   seed_viewer   (0000-…-0001)  The account you log in as to call the feed
--   seed_author1  (0000-…-0002)  Followed by viewer (accepted); 3 published posts
--   seed_author2  (0000-…-0003)  Followed by viewer (accepted); 2 published posts + 1 soft-deleted
--   seed_pending  (0000-…-0004)  Viewer has PENDING follow → post excluded (pending-follow rule)
--   seed_vblocks  (0000-…-0005)  Viewer follows (accepted) AND viewer blocks → excluded (viewer-blocks rule)
--   seed_bviewer  (0000-…-0006)  Viewer follows (accepted) AND they block viewer → excluded (blocked-by rule)
--   seed_nofollow (0000-…-0007)  Viewer does not follow → excluded (no-follow rule)
--
-- FOLLOWS
--   viewer → author1  accepted  chronological ordering across two authors
--   viewer → author2  accepted  chronological ordering across two authors
--   viewer → pending  pending   pending-follow exclusion
--   viewer → vblocks  accepted  viewer-blocks exclusion (paired with block row)
--   viewer → bviewer  accepted  blocked-by exclusion (paired with block row)
--
-- BLOCKS
--   viewer  → vblocks   viewer initiates block; vblocks removed from authorIds
--   bviewer → viewer    target initiates block; bviewer removed from authorIds
--
-- POSTS (12 total)
--   post_a1_p1  author1 published 2025-06-01 09:00 UTC  APPEARS in feed — position 4
--   post_a1_p2  author1 published 2025-06-01 10:00 UTC  APPEARS in feed — position 3
--   post_a1_p3  author1 published 2025-06-01 11:00 UTC  APPEARS in feed — position 2
--   post_a1_d   author1 draft                           EXCLUDED — non-published status
--   post_a2_p1  author2 published 2025-06-01 08:00 UTC  APPEARS in feed — position 5
--   post_a2_p2  author2 published 2025-06-01 12:30 UTC  APPEARS in feed — position 1 (newest)
--   post_a2_del author2 removed  deleted_at set          EXCLUDED — soft-delete (status=removed)
--   post_vwn    viewer  published                        EXCLUDED — viewer.id not in authorIds (self)
--   post_pend   pending published                        EXCLUDED — pending follow not in authorIds
--   post_vblk   vblocks published                        EXCLUDED — viewer blocks vblocks
--   post_bvwr   bviewer published                        EXCLUDED — bviewer blocks viewer
--   post_nofol  nofollow published                       EXCLUDED — not followed
--
-- NOTE: denormalized counters (follower_count, following_count, post_count, etc.) are NOT set.
-- Triggers trg_follow_counts and trg_post_count fire on these INSERTs and maintain them
-- automatically, per this project's GLOBAL_RULES policy.
-- =============================================================================

-- Defense-in-depth guard: skip all seed DML when the session declares app.environment = 'prod'.
-- current_setting(..., true) returns NULL when the GUC is unset (dev/test default), so the seed
-- still runs everywhere except an explicitly production-tagged session.
DO $$
BEGIN
    IF current_setting('app.environment', true) IS DISTINCT FROM 'prod' THEN

        -- USERS
        INSERT INTO users (id, username, email, display_name, role, status, is_private, is_verified)
        VALUES
            ('00000000-0000-0000-0000-000000000001', 'seed_viewer',
             'seed_viewer@seed.test',   'Seed Viewer',   'user', 'active', false, false),
            ('00000000-0000-0000-0000-000000000002', 'seed_author1',
             'seed_author1@seed.test',  'Seed Author 1', 'user', 'active', false, false),
            ('00000000-0000-0000-0000-000000000003', 'seed_author2',
             'seed_author2@seed.test',  'Seed Author 2', 'user', 'active', false, false),
            ('00000000-0000-0000-0000-000000000004', 'seed_pending',
             'seed_pending@seed.test',  'Seed Pending',  'user', 'active', false, false),
            ('00000000-0000-0000-0000-000000000005', 'seed_vblocks',
             'seed_vblocks@seed.test',  'Seed VBlocks',  'user', 'active', false, false),
            ('00000000-0000-0000-0000-000000000006', 'seed_bviewer',
             'seed_bviewer@seed.test',  'Seed BViewer',  'user', 'active', false, false),
            ('00000000-0000-0000-0000-000000000007', 'seed_nofollow',
             'seed_nofollow@seed.test', 'Seed NoFollow', 'user', 'active', false, false);

        -- CREDENTIALS — email_verified=true and status=active required by UserStateValidator
        INSERT INTO user_credentials (user_id, password_hash, email_verified, email_verified_at)
        VALUES
            ('00000000-0000-0000-0000-000000000001',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW()),
            ('00000000-0000-0000-0000-000000000002',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW()),
            ('00000000-0000-0000-0000-000000000003',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW()),
            ('00000000-0000-0000-0000-000000000004',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW()),
            ('00000000-0000-0000-0000-000000000005',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW()),
            ('00000000-0000-0000-0000-000000000006',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW()),
            ('00000000-0000-0000-0000-000000000007',
             '$2a$12$XbqgL0b95OtveP9Tto7ZGuYWrUmYqkrD5KRwMelqBI9UnX3hLVMgG', true, NOW());

        -- FOLLOWS  (trg_follow_counts maintains follower_count / following_count automatically)
        INSERT INTO follows (follower_id, following_id, status)
        VALUES
            ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'accepted'),
            ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'accepted'),
            ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', 'pending'),
            ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005', 'accepted'),
            ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000006', 'accepted');

        -- BLOCKS
        -- SocialServiceImpl.blockUser() normally deletes the follow when a block is created;
        -- these rows are inserted directly to test the exclusion filter in getAcceptedFollowingExcludingBlocks.
        INSERT INTO blocks (blocker_id, blocked_id)
        VALUES
            ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005'),
            ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001');

        -- POSTS  (trg_post_count maintains post_count on users automatically)
        -- No post_media rows inserted; the feed query does not require them.

        -- author1: 3 published posts at distinct timestamps
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0001-0000-000000000001', '00000000-0000-0000-0000-000000000002',
             'seed feed post A1-P1', 'image', 'published', '2025-06-01 09:00:00+00'),
            ('00000000-0000-0001-0000-000000000002', '00000000-0000-0000-0000-000000000002',
             'seed feed post A1-P2', 'image', 'published', '2025-06-01 10:00:00+00'),
            ('00000000-0000-0001-0000-000000000003', '00000000-0000-0000-0000-000000000002',
             'seed feed post A1-P3', 'image', 'published', '2025-06-01 11:00:00+00');

        -- author1: draft post
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0001-0000-000000000004', '00000000-0000-0000-0000-000000000002',
             'seed feed post A1-DRAFT', 'image', 'draft', '2025-06-01 11:30:00+00');

        -- author2: 2 published posts interspersed with author1 timestamps (cross-author ordering)
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0002-0000-000000000001', '00000000-0000-0000-0000-000000000003',
             'seed feed post A2-P1', 'image', 'published', '2025-06-01 08:00:00+00'),
            ('00000000-0000-0002-0000-000000000002', '00000000-0000-0000-0000-000000000003',
             'seed feed post A2-P2', 'image', 'published', '2025-06-01 12:30:00+00');

        -- author2: soft-deleted post (PostServiceImpl.softDelete sets status=removed AND deleted_at)
        INSERT INTO posts (id, user_id, caption, post_type, status, deleted_at, created_at)
        VALUES
            ('00000000-0000-0002-0000-000000000003', '00000000-0000-0000-0000-000000000003',
             'seed feed post A2-DELETED', 'image', 'removed', '2025-06-01 13:00:00+00',
             '2025-06-01 07:00:00+00');

        -- viewer own post
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0003-0000-000000000001', '00000000-0000-0000-0000-000000000001',
             'seed feed post VIEWER-OWN', 'image', 'published', '2025-06-01 13:00:00+00');

        -- pending author post
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0004-0000-000000000001', '00000000-0000-0000-0000-000000000004',
             'seed feed post PENDING', 'image', 'published', '2025-06-01 12:00:00+00');

        -- vblocks author post
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0005-0000-000000000001', '00000000-0000-0000-0000-000000000005',
             'seed feed post VBLOCKS', 'image', 'published', '2025-06-01 12:00:00+00');

        -- bviewer author post
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0006-0000-000000000001', '00000000-0000-0000-0000-000000000006',
             'seed feed post BVIEWER', 'image', 'published', '2025-06-01 12:00:00+00');

        -- nofollow author post
        INSERT INTO posts (id, user_id, caption, post_type, status, created_at)
        VALUES
            ('00000000-0000-0007-0000-000000000001', '00000000-0000-0000-0000-000000000007',
             'seed feed post NOFOLLOW', 'image', 'published', '2025-06-01 12:00:00+00');

    END IF;
END $$;
