-- Flyway migration V14
-- Source: database/schema.sql lines 475-578
-- Recommendation module: categories, user_interests, post_categories,
-- user_events (PARTITION BY RANGE created_at) plus 17 monthly partitions and a default,
-- post_interaction_scores (background-scheduler-managed), user_similarity (ML-job-managed).

CREATE TABLE categories (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    UNIQUE NOT NULL,
    slug                VARCHAR(100)    UNIQUE NOT NULL,
    parent_id           UUID            REFERENCES categories(id),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE user_interests (
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id         UUID            NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    score               DECIMAL(5,2)    NOT NULL DEFAULT 1.0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, category_id)
);

CREATE TABLE post_categories (
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    category_id         UUID            NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    confidence          DECIMAL(4,3)    NOT NULL DEFAULT 1.0,
    PRIMARY KEY (post_id, category_id)
);

CREATE TABLE user_events (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id          UUID,
    event_type          event_type      NOT NULL,
    entity_type         VARCHAR(50),
    entity_id           UUID,
    metadata            JSONB,
    ip_address          INET,
    user_agent          TEXT,
    platform            VARCHAR(10)     CHECK (platform IN ('ios', 'android', 'web')),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

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
CREATE TABLE user_events_default PARTITION OF user_events DEFAULT;

CREATE TABLE post_interaction_scores (
    post_id             UUID            PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
    view_score          DECIMAL(10,4)   NOT NULL DEFAULT 0,
    engagement_score    DECIMAL(10,4)   NOT NULL DEFAULT 0,
    recency_score       DECIMAL(10,4)   NOT NULL DEFAULT 0,
    total_score         DECIMAL(10,4)   NOT NULL DEFAULT 0,
    computed_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE user_similarity (
    user_id_a           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_id_b           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    similarity_score    DECIMAL(6,4)    NOT NULL CHECK (similarity_score BETWEEN 0 AND 1),
    computed_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id_a, user_id_b),
    CHECK (user_id_a < user_id_b)
);
