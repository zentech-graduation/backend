-- Flyway migration V01
-- Source: database/schema.sql lines 9-54
-- Creates required PostgreSQL extensions and the 14 domain enum types.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

CREATE TYPE user_role         AS ENUM ('user', 'moderator', 'admin');
CREATE TYPE user_status       AS ENUM ('active', 'suspended', 'deactivated', 'banned');
CREATE TYPE post_status       AS ENUM ('draft', 'published', 'archived', 'removed');
CREATE TYPE post_type         AS ENUM ('image', 'video', 'carousel');
CREATE TYPE media_type        AS ENUM ('image', 'video');
CREATE TYPE follow_status     AS ENUM ('pending', 'accepted');
CREATE TYPE story_type        AS ENUM ('image', 'video');
CREATE TYPE message_type      AS ENUM ('text', 'image', 'video', 'post_share', 'story_share');
CREATE TYPE report_type       AS ENUM ('post', 'comment', 'user', 'story', 'message');
CREATE TYPE report_status     AS ENUM ('pending', 'reviewing', 'resolved', 'dismissed');

CREATE TYPE report_reason     AS ENUM (
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

CREATE TYPE oauth_provider    AS ENUM ('google', 'facebook', 'apple');

CREATE TYPE admin_action_type AS ENUM (
    'ban_user', 'unban_user', 'suspend_user', 'unsuspend_user',
    'remove_post', 'restore_post',
    'remove_comment', 'restore_comment',
    'resolve_report', 'dismiss_report'
);

CREATE TYPE event_type        AS ENUM (
    'post_view', 'post_like', 'post_unlike',
    'post_save', 'post_unsave', 'post_share', 'post_comment',
    'story_view', 'story_reply',
    'profile_view', 'profile_follow', 'profile_unfollow',
    'search', 'hashtag_click',
    'comment_like', 'comment_reply',
    'message_send',
    'session_start', 'session_end', 'app_open'
);
