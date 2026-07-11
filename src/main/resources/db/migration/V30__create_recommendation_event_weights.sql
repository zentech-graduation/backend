-- Flyway migration V30
-- Recommendation module: event weight config table.
-- This is the metadata layer for the event_type enum (the constraint layer), following the
-- GLOBAL_RULES enum-vs-config-table contract (see notification_type_configs precedent).
-- The Java trending updater, the blend scorer, and the Python batch all read the same weights
-- from this table, so tuning never requires code changes.
-- Undo actions (post_unlike, post_unsave) are ordinary rows with negative weights: no
-- special-case code anywhere. The PRIMARY KEY on event_type mirrors the enum constraint.

CREATE TABLE recommendation_event_weights (
    event_type       event_type    PRIMARY KEY,
    weight           DECIMAL(6,3)  NOT NULL DEFAULT 0,
    cf_enabled       BOOLEAN       NOT NULL DEFAULT FALSE,
    trending_enabled BOOLEAN       NOT NULL DEFAULT FALSE,
    affinity_enabled BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

INSERT INTO recommendation_event_weights (event_type, weight, cf_enabled, trending_enabled, affinity_enabled) VALUES
    ('post_view',      1.0, TRUE,  TRUE,  TRUE),
    ('post_like',      3.0, TRUE,  TRUE,  TRUE),
    ('post_comment',   4.0, TRUE,  TRUE,  TRUE),
    ('post_save',      4.0, TRUE,  TRUE,  TRUE),
    ('post_share',     5.0, TRUE,  TRUE,  TRUE),
    ('post_unlike',   -3.0, TRUE,  FALSE, TRUE),
    ('post_unsave',   -4.0, TRUE,  FALSE, TRUE),
    ('story_view',     0.0, FALSE, FALSE, FALSE),
    ('story_reply',    0.0, FALSE, FALSE, FALSE),
    ('profile_view',   0.0, FALSE, FALSE, FALSE),
    ('profile_follow', 0.0, FALSE, FALSE, TRUE),
    ('profile_unfollow', 0.0, FALSE, FALSE, TRUE),
    ('search',         0.0, FALSE, FALSE, FALSE),
    ('hashtag_click',  0.0, FALSE, FALSE, FALSE),
    ('comment_like',   0.0, FALSE, FALSE, FALSE),
    ('comment_reply',  0.0, FALSE, FALSE, FALSE),
    ('message_send',   0.0, FALSE, FALSE, FALSE),
    ('session_start',  0.0, FALSE, FALSE, FALSE),
    ('session_end',    0.0, FALSE, FALSE, FALSE),
    ('app_open',       0.0, FALSE, FALSE, FALSE);
