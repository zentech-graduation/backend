-- ============================================================
-- V18: Metadata Configuration Tables
-- Runtime configuration and feature policy management.
-- These tables allow policy changes without code redeployment.
-- They must never store secrets, credentials, or env-specific values.
-- ============================================================

-- ============================================================
-- TABLE: system_settings
-- System-wide configurable limits and TTL values.
-- ============================================================

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

CREATE TRIGGER trg_system_settings_updated_at
    BEFORE UPDATE ON system_settings
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- TABLE: notification_type_configs
-- Registry of notification types with display and toggle settings.
-- type_key maps to values in the notification_type enum.
-- ============================================================

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

-- ============================================================
-- TABLE: moderation_action_configs
-- Registry of admin moderation actions with behavioral flags.
-- action_key maps to values in the admin_action_type enum.
-- ============================================================

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

-- ============================================================
-- TABLE: feature_flags
-- Feature enable/disable control with optional environment scoping.
-- ============================================================

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

CREATE TRIGGER trg_feature_flags_updated_at
    BEFORE UPDATE ON feature_flags
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- TABLE: report_reason_configs
-- Display metadata and policy configuration for each report reason.
-- reason_key maps to values in the report_reason enum.
-- applies_to controls which report_type values this reason is valid for;
-- empty array means the reason applies to all report types.
-- ============================================================

CREATE TABLE report_reason_configs (
    reason_key          VARCHAR(100)    NOT NULL,
    display_name        VARCHAR(100)    NOT NULL,
    description         TEXT,
    applies_to          VARCHAR(50)[]   NOT NULL DEFAULT '{}',
    -- Array of report_type values this reason is valid for.
    -- e.g. ARRAY['post', 'comment', 'story'] means this reason does not appear for 'user' or 'message' reports.
    -- Empty array means applies to all report types.
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
