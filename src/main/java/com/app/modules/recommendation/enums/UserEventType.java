package com.app.modules.recommendation.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Behavioural event types persisted in the PostgreSQL {@code event_type} enum.
 *
 * <p>Every value the database declares is listed, because the activity log filters on this column
 * and a filter that cannot name a stored value would be a hole in the read surface. Only {@link
 * #SESSION_START}, {@link #SEARCH} and {@link #PROFILE_VIEW} have a writer; the rest exist in the
 * schema and are never produced. See {@code UserEventRecorder} for why the write set is
 * deliberately this small.
 */
public enum UserEventType {
    POST_VIEW,
    POST_LIKE,
    POST_UNLIKE,
    POST_SAVE,
    POST_UNSAVE,
    POST_SHARE,
    POST_COMMENT,
    STORY_VIEW,
    STORY_REPLY,
    PROFILE_VIEW,
    PROFILE_FOLLOW,
    PROFILE_UNFOLLOW,
    SEARCH,
    HASHTAG_CLICK,
    COMMENT_LIKE,
    COMMENT_REPLY,
    MESSAGE_SEND,
    SESSION_START,
    SESSION_END,
    APP_OPEN;

    @JsonCreator
    public static UserEventType fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : UserEventType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
