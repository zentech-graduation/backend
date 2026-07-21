-- ============================================================
-- PostgreSQL
-- ============================================================

-- ============================================================
-- EXTENSIONS
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";    -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";     -- fuzzy search on username
CREATE EXTENSION IF NOT EXISTS "btree_gin";   -- composite GIN indexes

-- ============================================================
-- ENUM TYPES
-- ============================================================

CREATE TYPE user_role       AS ENUM ('user', 'moderator', 'admin');
CREATE TYPE user_status     AS ENUM ('active', 'suspended', 'deactivated', 'banned');
CREATE TYPE post_status     AS ENUM ('draft', 'published', 'archived', 'removed');
CREATE TYPE post_type       AS ENUM ('image', 'video', 'carousel', 'text');
CREATE TYPE media_type      AS ENUM ('image', 'video');
CREATE TYPE follow_status   AS ENUM ('pending', 'accepted');
CREATE TYPE story_type      AS ENUM ('image', 'video');
CREATE TYPE message_type    AS ENUM ('text', 'image', 'video', 'post_share', 'story_share');
CREATE TYPE report_type     AS ENUM ('post', 'comment', 'user', 'story', 'message');
CREATE TYPE report_status   AS ENUM ('pending', 'reviewing', 'resolved', 'dismissed');
CREATE TYPE report_reason   AS ENUM (
    'spam', 'nudity', 'violence', 'hate_speech',
    'harassment', 'false_information', 'scam', 'other'
);
CREATE TYPE notification_type AS ENUM (
    'like_post', 'like_comment',
    'comment_post', 'reply_comment',
    'follow', 'follow_request',
    'mention_post', 'mention_comment',
    'story_view', 'message'
);
CREATE TYPE oauth_provider  AS ENUM ('google', 'facebook', 'apple');
CREATE TYPE admin_action_type AS ENUM (
    'ban_user', 'unban_user', 'suspend_user', 'unsuspend_user',
    'remove_post', 'restore_post',
    'remove_comment', 'restore_comment',
    'resolve_report', 'dismiss_report'
);
CREATE TYPE event_type AS ENUM (
    'post_view', 'post_like', 'post_unlike',
    'post_save', 'post_unsave', 'post_share', 'post_comment',
    'story_view', 'story_reply',
    'profile_view', 'profile_follow', 'profile_unfollow',
    'search', 'hashtag_click',
    'comment_like', 'comment_reply',
    'message_send',
    'session_start', 'session_end', 'app_open'
);

-- ============================================================
-- MODULE: AUTH
-- ============================================================

-- Core user entity
CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(30)     UNIQUE NOT NULL,
    email               VARCHAR(255)    UNIQUE NOT NULL,
    display_name        VARCHAR(100),
    bio                 TEXT,
    avatar_url          TEXT,
    website_url         TEXT,
    role                user_role       NOT NULL DEFAULT 'user',
    status              user_status     NOT NULL DEFAULT 'active',
    is_private          BOOLEAN         NOT NULL DEFAULT FALSE,
    is_verified         BOOLEAN         NOT NULL DEFAULT FALSE,
    -- Denormalized counters (maintained by triggers)
    follower_count      INT             NOT NULL DEFAULT 0 CHECK (follower_count >= 0),
    following_count     INT             NOT NULL DEFAULT 0 CHECK (following_count >= 0),
    post_count          INT             NOT NULL DEFAULT 0 CHECK (post_count >= 0),
    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ     -- soft delete
);

-- Local credentials (email + password login)
-- password_hash is nullable to support OAuth-only users who have no local password
CREATE TABLE user_credentials (
    user_id             UUID            PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    password_hash       TEXT,
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- OAuth provider accounts (Google, etc.)
-- Note: access_token, refresh_token, token_expires_at were dropped in V23 (drop_legacy_plaintext_credential_columns).
-- These columns will be re-added with encrypted storage when that feature is implemented.
CREATE TABLE oauth_accounts (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider            oauth_provider  NOT NULL,
    provider_id         VARCHAR(255)    NOT NULL,
    provider_email      VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_id)
);

-- JWT refresh tokens (stored as hash for security)
CREATE TABLE refresh_tokens (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash          TEXT            NOT NULL UNIQUE,
    device_id           VARCHAR(255),
    user_agent          TEXT,
    ip_address          INET,
    expires_at          TIMESTAMPTZ     NOT NULL,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Note: email verification tokens and password reset tokens are stored in Redis
-- (see TokenServiceImpl), not in the relational database.

-- ============================================================
-- MODULE: USER PROFILE & SETTINGS
-- ============================================================

-- Per-user notification and privacy settings
CREATE TABLE user_settings (
    user_id                 UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    -- Notification toggles
    notify_likes            BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_comments         BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_follows          BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_mentions         BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_messages         BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Privacy
    show_activity_status    BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_story_replies     BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_message_requests  BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Device push notification tokens
CREATE TABLE push_tokens (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token               TEXT            NOT NULL UNIQUE,
    platform            VARCHAR(10)     NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_used_at        TIMESTAMPTZ
);

-- ============================================================
-- MODULE: SOCIAL GRAPH (Follow / Block)
-- ============================================================

-- Follow relationships (unidirectional, like Instagram)
CREATE TABLE follows (
    follower_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id        UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status              follow_status   NOT NULL DEFAULT 'accepted',
    -- status = 'pending' when target account is private
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CHECK (follower_id <> following_id)
);

-- Block relationships
CREATE TABLE blocks (
    blocker_id          UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id          UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

-- ============================================================
-- MODULE: MEDIA ASSETS (Cloudflare R2)
-- ============================================================
-- Decision: store full metadata (width, height, duration, blurhash)
-- Reasons:
--   1. Aspect ratio needed for responsive UI layout (no extra API call)
--   2. Video duration required for player rendering
--   3. Blurhash provides loading placeholder (same as Instagram)
--   4. Avoids repeated HEAD requests to CDN per render

CREATE TABLE media_assets (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_key         TEXT            NOT NULL UNIQUE,  -- R2 object key
    cdn_url             TEXT            NOT NULL,
    media_type          media_type      NOT NULL,
    mime_type           VARCHAR(100)    NOT NULL,
    file_size           BIGINT          NOT NULL CHECK (file_size > 0),  -- bytes
    width               INT             CHECK (width > 0),               -- pixels
    height              INT             CHECK (height > 0),
    duration            INT             CHECK (duration >= 0),           -- seconds (video only)
    blurhash            VARCHAR(100),                                     -- loading placeholder
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODULE: POST
-- ============================================================

CREATE TABLE posts (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    caption             TEXT,
    post_type           post_type       NOT NULL DEFAULT 'image',
    status              post_status     NOT NULL DEFAULT 'published',
    -- Denormalized counters for read performance
    like_count          INT             NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count       INT             NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    save_count          INT             NOT NULL DEFAULT 0 CHECK (save_count >= 0),
    view_count          INT             NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    -- Optional location metadata
    location_name       VARCHAR(255),
    latitude            DECIMAL(10,8),
    longitude           DECIMAL(11,8),
    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Post media items (supports carousel: multiple images/videos)
CREATE TABLE post_media (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    media_asset_id      UUID            NOT NULL REFERENCES media_assets(id),
    position            SMALLINT        NOT NULL DEFAULT 0,  -- carousel ordering
    alt_text            TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, position)
);

-- User tags within a post image
CREATE TABLE post_user_tags (
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tagged_user_id      UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_asset_id      UUID            REFERENCES media_assets(id),
    x_position          DECIMAL(5,2),   -- percentage on image
    y_position          DECIMAL(5,2),
    PRIMARY KEY (post_id, tagged_user_id)
);

-- Post likes (compound PK = one like per user per post)
CREATE TABLE post_likes (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);

-- Post saves / bookmarks
CREATE TABLE post_saves (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);

-- Post caption edit history (append-only audit; rows are never updated or soft-deleted)
CREATE TABLE post_edit_history (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    editor_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    previous_caption    TEXT,
    edited_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODULE: COMMENTS (Nested, Adjacency List + root_id + depth)
-- ============================================================
-- Design: Adjacency list with root_id + depth columns.
-- Strategy:
--   - parent_id  → immediate parent (standard adjacency list)
--   - root_id    → top-level comment ancestor (null if top-level)
--   - depth      → nesting level (0 = top-level, 1 = direct reply, ...)
--   - Depth capped at 10 to prevent runaway nesting.
--   - Retrieval of subtrees uses WITH RECURSIVE CTE (built into PostgreSQL).
--   - This is simpler than Closure Table while remaining maintainable
--     for a student team; sufficient for Instagram-style 2-level display.

CREATE TABLE comments (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id           UUID            REFERENCES comments(id) ON DELETE CASCADE,
    root_id             UUID            REFERENCES comments(id) ON DELETE CASCADE,
    depth               SMALLINT        NOT NULL DEFAULT 0 CHECK (depth BETWEEN 0 AND 10),
    content             TEXT            NOT NULL,
    moderation_status   VARCHAR(20)     NOT NULL DEFAULT 'approved',
    -- Denormalized counters
    like_count          INT             NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    reply_count         INT             NOT NULL DEFAULT 0 CHECK (reply_count >= 0),
    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Comment likes
CREATE TABLE comment_likes (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    comment_id          UUID            NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, comment_id)
);

-- Comment write idempotency: caches the first response per (user_id, idempotency_key).
CREATE TABLE comment_write_idempotency (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key     VARCHAR(64)     NOT NULL,
    request_hash        VARCHAR(64)     NOT NULL,
    response_body       JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, idempotency_key)
);

-- ============================================================
-- MODULE: HASHTAG & TRENDING
-- ============================================================

CREATE TABLE hashtags (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    UNIQUE NOT NULL,  -- stored without #
    post_count          INT             NOT NULL DEFAULT 0 CHECK (post_count >= 0),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE post_hashtags (
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    hashtag_id          UUID            NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, hashtag_id)
);

-- Trending snapshots: populated by a scheduled background job
CREATE TABLE hashtag_trending (
    hashtag_id          UUID            NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    period_start        TIMESTAMPTZ     NOT NULL,
    period_end          TIMESTAMPTZ     NOT NULL,
    post_count          INT             NOT NULL DEFAULT 0,
    rank                INT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (hashtag_id, period_start)
);

-- ============================================================
-- MODULE: STORY (24-hour Ephemeral)
-- ============================================================

CREATE TABLE stories (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_asset_id      UUID            NOT NULL REFERENCES media_assets(id),
    story_type          story_type      NOT NULL DEFAULT 'image',
    caption             TEXT,
    view_count          INT             NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    expires_at          TIMESTAMPTZ     NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Story views (deduplicated per viewer)
CREATE TABLE story_views (
    story_id            UUID            NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    viewer_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (story_id, viewer_id)
);

-- ============================================================
-- MODULE: NOTIFICATIONS
-- ============================================================

CREATE TABLE notifications (
    id                  UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        UUID                NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_id            UUID                REFERENCES users(id) ON DELETE SET NULL,
    type                notification_type   NOT NULL,
    -- Polymorphic target entity
    entity_type         VARCHAR(50),    -- 'post' | 'comment' | 'story' | 'follow' | 'message'
    entity_id           UUID,
    is_read             BOOLEAN             NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODULE: DIRECT MESSAGE / CHAT
-- ============================================================

-- A conversation can be 1-1 or group
CREATE TABLE conversations (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group            BOOLEAN         NOT NULL DEFAULT FALSE,
    group_name          VARCHAR(100),
    group_avatar_url    TEXT,
    created_by          UUID            REFERENCES users(id) ON DELETE SET NULL,
    last_message_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Members of a conversation
CREATE TABLE conversation_participants (
    conversation_id     UUID            NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_admin            BOOLEAN         NOT NULL DEFAULT FALSE,
    joined_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    left_at             TIMESTAMPTZ,
    last_read_at        TIMESTAMPTZ,    -- for unread badge calculation
    PRIMARY KEY (conversation_id, user_id)
);

-- Individual messages
CREATE TABLE messages (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID            NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id           UUID            REFERENCES users(id) ON DELETE SET NULL,
    message_type        message_type    NOT NULL DEFAULT 'text',
    content             TEXT,
    media_asset_id      UUID            REFERENCES media_assets(id),
    -- Shared content references
    shared_post_id      UUID            REFERENCES posts(id) ON DELETE SET NULL,
    shared_story_id     UUID            REFERENCES stories(id) ON DELETE SET NULL,
    -- Thread reply
    reply_to_id         UUID            REFERENCES messages(id) ON DELETE SET NULL,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODULE: REPORT (Content Flagging)
-- ============================================================

CREATE TABLE reports (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_type         report_type     NOT NULL,
    report_reason       report_reason   NOT NULL,
    entity_id           UUID            NOT NULL,   -- polymorphic: post/comment/user/story/message
    description         TEXT,
    status              report_status   NOT NULL DEFAULT 'pending',
    reviewed_by         UUID            REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at         TIMESTAMPTZ,
    resolution_note     TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODULE: ADMIN (Moderation Audit Log)
-- ============================================================

CREATE TABLE admin_actions (
    id                      UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id                UUID                REFERENCES users(id) ON DELETE SET NULL,
    action_type             admin_action_type   NOT NULL,
    target_user_id          UUID                REFERENCES users(id) ON DELETE SET NULL,
    target_entity_type      VARCHAR(50),
    target_entity_id        UUID,
    report_id               UUID                REFERENCES reports(id) ON DELETE SET NULL,
    reason                  TEXT,
    metadata                JSONB,
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MODULE: RECOMMENDATION SYSTEM & USER BEHAVIOR
-- ============================================================

-- Interest taxonomy (e.g. Fashion > Streetwear)
CREATE TABLE categories (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    UNIQUE NOT NULL,
    slug                VARCHAR(100)    UNIQUE NOT NULL,
    parent_id           UUID            REFERENCES categories(id),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- User interest weights (updated by ML jobs or explicit selection)
CREATE TABLE user_interests (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id         UUID            NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    score               DECIMAL(5,2)    NOT NULL DEFAULT 1.0,  -- interest weight [0, 10]
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, category_id)
);

-- Post categorisation (assigned during upload or by ML classifier)
CREATE TABLE post_categories (
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    category_id         UUID            NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    confidence          DECIMAL(4,3)    NOT NULL DEFAULT 1.0,  -- ML confidence [0,1]
    PRIMARY KEY (post_id, category_id)
);

-- Raw event stream for user behavior
-- Partitioned by month: high-volume, append-only, analytics-friendly
CREATE TABLE user_events (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id          UUID,
    event_type          event_type      NOT NULL,
    entity_type         VARCHAR(50),    -- 'post' | 'user' | 'hashtag' | 'story' | 'comment'
    entity_id           UUID,
    metadata            JSONB,          -- extra context (search_query, scroll_depth, etc.)
    ip_address          INET,
    user_agent          TEXT,
    platform            VARCHAR(10)     CHECK (platform IN ('ios', 'android', 'web')),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Monthly partitions for user_events
-- Tip: use pg_partman extension to auto-create future partitions in production
CREATE TABLE user_events_2025_01 PARTITION OF user_events
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE user_events_2025_02 PARTITION OF user_events
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE user_events_2025_03 PARTITION OF user_events
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE user_events_2025_04 PARTITION OF user_events
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE user_events_2025_05 PARTITION OF user_events
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE user_events_2025_06 PARTITION OF user_events
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE user_events_2025_07 PARTITION OF user_events
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE user_events_2025_08 PARTITION OF user_events
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE user_events_2025_09 PARTITION OF user_events
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE user_events_2025_10 PARTITION OF user_events
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE user_events_2025_11 PARTITION OF user_events
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE user_events_2025_12 PARTITION OF user_events
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');
CREATE TABLE user_events_2026_01 PARTITION OF user_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE user_events_2026_02 PARTITION OF user_events
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE user_events_2026_03 PARTITION OF user_events
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE user_events_2026_04 PARTITION OF user_events
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE user_events_2026_05 PARTITION OF user_events
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE user_events_2026_06 PARTITION OF user_events
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
-- V28 opened a rolling window past the original 2026-06 ceiling; UserEventsPartitionJob keeps it
-- advancing (creates the partition two months ahead each month).
CREATE TABLE user_events_2026_07 PARTITION OF user_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE user_events_2026_08 PARTITION OF user_events
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE user_events_2026_09 PARTITION OF user_events
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE user_events_default PARTITION OF user_events DEFAULT;

-- Aggregated post interaction scores (updated by background scheduler)
-- Powers feed ranking and Explore page recommendation
CREATE TABLE post_interaction_scores (
    post_id             UUID            PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
    view_score          DECIMAL(10,4)   NOT NULL DEFAULT 0,
    engagement_score    DECIMAL(10,4)   NOT NULL DEFAULT 0,  -- weighted: likes + comments*2 + saves*3
    recency_score       DECIMAL(10,4)   NOT NULL DEFAULT 0,  -- time decay factor
    total_score         DECIMAL(10,4)   NOT NULL DEFAULT 0,  -- final ranking score
    computed_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- User-to-user similarity for collaborative filtering (populated by ML jobs)
-- Constraint: store only (min_id, max_id) to avoid (A,B) / (B,A) duplication
CREATE TABLE user_similarity (
    user_id_a           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_id_b           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    similarity_score    DECIMAL(6,4)    NOT NULL CHECK (similarity_score BETWEEN 0 AND 1),
    computed_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id_a, user_id_b),
    CHECK (user_id_a < user_id_b)
);

-- ============================================================
-- MODULE: COMMON / OUTBOX
-- ============================================================

CREATE TABLE outbox_events (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL UNIQUE,
    aggregate_type      VARCHAR(100)    NOT NULL,
    aggregate_id        UUID            NOT NULL,
    event_type          VARCHAR(150)    NOT NULL,
    routing_key         VARCHAR(150)    NOT NULL,
    payload             JSONB           NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD')),
    attempt_count       INTEGER         NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_retry_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    claim_id            UUID,
    claimed_at          TIMESTAMPTZ,
    claimed_until       TIMESTAMPTZ,
    last_error          TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CHECK (
        status <> 'PROCESSING'
        OR (
            claim_id IS NOT NULL
            AND claimed_at IS NOT NULL
            AND claimed_until IS NOT NULL
            AND claimed_until > claimed_at
        )
    )
);

CREATE TABLE processed_messages (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_name       VARCHAR(100)    NOT NULL,
    event_id            UUID            NOT NULL,
    event_type          VARCHAR(150)    NOT NULL,
    processed_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (consumer_name, event_id)
);

-- ============================================================
-- MODULE: METADATA CONFIG
-- ============================================================

-- System-wide configurable limits and TTL values
CREATE TABLE system_settings (
    key             VARCHAR(100)    PRIMARY KEY,
    value           TEXT            NOT NULL,
    description     TEXT,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO system_settings (key, value, description) VALUES
    ('max_comment_depth',                '10',  'Maximum nesting depth for comments'),
    ('story_duration_hours',             '24',  'How long a story remains visible after posting'),
    ('max_media_size_mb',                '100', 'Maximum allowed media file size in megabytes'),
    ('max_hashtags_per_post',            '30',  'Maximum number of hashtags allowed per post'),
    ('max_post_media_items',             '10',  'Maximum media items in a single post or carousel'),
    ('password_reset_token_ttl_minutes', '15',  'Password reset token validity in minutes'),
    ('email_verify_token_ttl_hours',     '24',  'Email verification token validity in hours'),
    ('default_rate_limit_per_minute',    '60',  'Default API rate limit per user per minute');

-- Registry of notification types with display and toggle settings
-- type_key maps to values in the notification_type enum
CREATE TABLE notification_type_configs (
    type_key            VARCHAR(100)    PRIMARY KEY,
    display_name        VARCHAR(100)    NOT NULL,
    template_key        VARCHAR(100),
    is_user_toggleable  BOOLEAN         NOT NULL DEFAULT TRUE,
    is_enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO notification_type_configs (type_key, display_name, template_key, is_user_toggleable, is_enabled) VALUES
    ('like_post',       'Like on Post',        'like_post',       TRUE, TRUE),
    ('like_comment',    'Like on Comment',     'like_comment',    TRUE, TRUE),
    ('comment_post',    'Comment on Post',     'comment_post',    TRUE, TRUE),
    ('reply_comment',   'Reply to Comment',    'reply_comment',   TRUE, TRUE),
    ('follow',          'Follow',              'follow',          TRUE, TRUE),
    ('follow_request',  'Follow Request',      'follow_request',  TRUE, TRUE),
    ('mention_post',    'Mention in Post',     'mention_post',    TRUE, TRUE),
    ('mention_comment', 'Mention in Comment',  'mention_comment', TRUE, TRUE),
    ('story_view',      'Story View',          'story_view',      TRUE, TRUE),
    ('message',         'Message',             'message',         TRUE, TRUE);

-- Registry of admin moderation actions with behavioral flags
-- action_key maps to values in the admin_action_type enum
CREATE TABLE moderation_action_configs (
    action_key          VARCHAR(100)    PRIMARY KEY,
    display_name        VARCHAR(100)    NOT NULL,
    requires_reason     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_reversible       BOOLEAN         NOT NULL DEFAULT TRUE,
    is_enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO moderation_action_configs (action_key, display_name, requires_reason, is_reversible, is_enabled) VALUES
    ('ban_user',        'Ban User',        TRUE,  FALSE, TRUE),
    ('unban_user',      'Unban User',      FALSE, FALSE, TRUE),
    ('suspend_user',    'Suspend User',    TRUE,  TRUE,  TRUE),
    ('unsuspend_user',  'Unsuspend User',  FALSE, TRUE,  TRUE),
    ('remove_post',     'Remove Post',     TRUE,  TRUE,  TRUE),
    ('restore_post',    'Restore Post',    FALSE, TRUE,  TRUE),
    ('remove_comment',  'Remove Comment',  TRUE,  TRUE,  TRUE),
    ('restore_comment', 'Restore Comment', FALSE, TRUE,  TRUE),
    ('resolve_report',  'Resolve Report',  FALSE, FALSE, TRUE),
    ('dismiss_report',  'Dismiss Report',  FALSE, FALSE, TRUE);

-- Feature enable/disable control with optional environment scoping
CREATE TABLE feature_flags (
    flag_key        VARCHAR(100)    PRIMARY KEY,
    is_enabled      BOOLEAN         NOT NULL DEFAULT FALSE,
    description     TEXT,
    environment     VARCHAR(20)     NOT NULL DEFAULT 'all'
                        CHECK (environment IN ('all', 'dev', 'staging', 'prod')),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO feature_flags (flag_key, is_enabled, description, environment) VALUES
    ('group_chat',       FALSE, 'Enable group conversation feature',                        'all'),
    ('recommendation',   FALSE, 'Enable personalized feed recommendation',                  'all'),
    ('story_reply',      TRUE,  'Allow users to reply to stories',                          'all'),
    ('maintenance_mode', FALSE, 'Put the application into read-only maintenance mode',      'all'),
    ('video_upload',     TRUE,  'Allow video file uploads',                                 'all');

-- Display metadata and policy configuration for each report reason
-- reason_key maps to values in the report_reason enum
-- applies_to: empty array means applies to all report types
CREATE TABLE report_reason_configs (
    reason_key          VARCHAR(100)    NOT NULL,
    display_name        VARCHAR(100)    NOT NULL,
    description         TEXT,
    applies_to          VARCHAR(50)[]   NOT NULL DEFAULT '{}',
    is_enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order          SMALLINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (reason_key)
);

INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('spam',             'Spam',                          '{}'::VARCHAR(50)[],                              TRUE, 1);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('nudity',           'Nudity or Sexual Content',      ARRAY['post','comment','story','message'],         TRUE, 2);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('violence',         'Violence or Dangerous Content', ARRAY['post','comment','story','message'],         TRUE, 3);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('hate_speech',      'Hate Speech',                   ARRAY['post','comment','story','message'],         TRUE, 4);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('harassment',       'Harassment or Bullying',        '{}'::VARCHAR(50)[],                              TRUE, 5);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('false_information','False Information',             ARRAY['post','comment','story'],                   TRUE, 6);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('scam',             'Scam or Fraud',                 '{}'::VARCHAR(50)[],                              TRUE, 7);
INSERT INTO report_reason_configs (reason_key, display_name, applies_to, is_enabled, sort_order) VALUES
    ('other',            'Other',                         '{}'::VARCHAR(50)[],                              TRUE, 99);

-- ============================================================
-- INDEXES
-- ============================================================

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

-- post_edit_history
CREATE INDEX idx_post_edit_history_post_edited
    ON post_edit_history (post_id, edited_at DESC);

-- comments
CREATE INDEX idx_comments_post_root     ON comments (post_id, created_at ASC)
    WHERE parent_id IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_comments_parent        ON comments (parent_id, created_at ASC)
    WHERE parent_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_comments_root          ON comments (root_id)
    WHERE root_id IS NOT NULL;
CREATE INDEX idx_comments_user          ON comments (user_id);
CREATE INDEX idx_comments_post_moderation ON comments (post_id, moderation_status, created_at DESC)
    WHERE deleted_at IS NULL;

-- comment_write_idempotency
CREATE INDEX idx_comment_idempotency_created ON comment_write_idempotency (created_at);

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
    WHERE deleted_at IS NULL;  -- used by cleanup job

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
CREATE UNIQUE INDEX uq_reports_reporter_type_entity ON reports (reporter_id, report_type, entity_id);

-- admin_actions
CREATE INDEX idx_admin_actions_admin    ON admin_actions (admin_id, created_at DESC);
CREATE INDEX idx_admin_actions_target   ON admin_actions (target_user_id)
    WHERE target_user_id IS NOT NULL;

-- refresh_tokens
CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- user_events (applied per partition automatically)
CREATE INDEX idx_user_events_user       ON user_events (user_id, created_at DESC);
CREATE INDEX idx_user_events_type       ON user_events (event_type, created_at DESC);
CREATE INDEX idx_user_events_entity     ON user_events (entity_type, entity_id, created_at DESC)
    WHERE entity_id IS NOT NULL;

-- post_interaction_scores
CREATE INDEX idx_post_scores_total      ON post_interaction_scores (total_score DESC);

-- user_similarity
CREATE INDEX idx_user_sim_score         ON user_similarity (user_id_a, similarity_score DESC);

-- outbox_events
CREATE INDEX idx_outbox_events_publish_scan
    ON outbox_events (status, next_retry_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at);

-- processed_messages
CREATE INDEX idx_processed_messages_event
    ON processed_messages (event_id);
CREATE INDEX idx_processed_messages_processed_at
    ON processed_messages (processed_at);

-- ============================================================
-- TRIGGERS & FUNCTIONS
-- ============================================================

-- Generic updated_at updater
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

CREATE TRIGGER trg_system_settings_updated_at
    BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_feature_flags_updated_at
    BEFORE UPDATE ON feature_flags
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- Follow counter maintenance
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

-- Post count on users
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

-- Post like_count
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

-- Post save_count
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

-- Post comment_count
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

-- Comment like_count
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

-- Comment reply_count (on parent)
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

-- Hashtag post_count
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

-- Story view_count
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

-- Update conversations.last_message_at on new message
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

-- ============================================================
-- USEFUL VIEWS
-- ============================================================

-- Active (non-expired, non-deleted) stories
CREATE OR REPLACE VIEW active_stories AS
SELECT s.*, u.username, u.avatar_url
FROM stories s
JOIN users u ON u.id = s.user_id
WHERE s.deleted_at IS NULL
  AND s.expires_at > NOW();

-- Pending follow requests (for private accounts)
CREATE OR REPLACE VIEW pending_follow_requests AS
SELECT f.*,
       follower.username   AS follower_username,
       follower.avatar_url AS follower_avatar
FROM follows f
JOIN users follower ON follower.id = f.follower_id
WHERE f.status = 'pending';

-- Pending moderation reports
CREATE OR REPLACE VIEW pending_reports AS
SELECT r.*,
       reporter.username AS reporter_username
FROM reports r
JOIN users reporter ON reporter.id = r.reporter_id
WHERE r.status = 'pending'
ORDER BY r.created_at ASC;

-- ============================================================
-- REFERENCE ARTIFACT
-- This file is auto-synced from Flyway migrations V01–V24.
-- Do NOT use this file as the authoritative schema source.
-- Authoritative source: src/main/resources/db/migration/
-- Last synced: 2026-06-30
-- ============================================================
