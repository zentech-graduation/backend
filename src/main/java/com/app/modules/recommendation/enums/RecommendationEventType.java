package com.app.modules.recommendation.enums;

/**
 * Mirror of the PostgreSQL {@code event_type} enum (Flyway V01) used by {@code user_events} and
 * {@code recommendation_event_weights}.
 *
 * <p>Kept in the recommendation module because recommendation is the dominant consumer of
 * behavioral events; other modules that need to classify an event type should depend on this enum.
 * Values are lowercase to match the PG enum exactly, and are converted to/from the database as
 * lowercase strings.
 */
public enum RecommendationEventType {
    post_view,
    post_like,
    post_unlike,
    post_save,
    post_unsave,
    post_share,
    post_comment,
    story_view,
    story_reply,
    profile_view,
    profile_follow,
    profile_unfollow,
    search,
    hashtag_click,
    comment_like,
    comment_reply,
    message_send,
    session_start,
    session_end,
    app_open
}
