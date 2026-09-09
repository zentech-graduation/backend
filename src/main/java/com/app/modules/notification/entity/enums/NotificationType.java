package com.app.modules.notification.entity.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationType {
    LIKE_POST,
    LIKE_COMMENT,
    COMMENT_POST,
    REPLY_COMMENT,
    FOLLOW,
    FOLLOW_REQUEST,
    MENTION_POST,
    MENTION_COMMENT,
    STORY_VIEW,
    MESSAGE,
    WARNING,
    // Answered, rejected or escalated. The mail carries the response text; this is the
    // in-product half, and like WARNING it is not user-toggleable: an account that could switch it
    // off would ask a question and never be told it had been answered.
    SUPPORT_TICKET_UPDATE,
    POST_REMOVED,
    REPORT_POST_REMOVED,
    POST_RESTORED,
    REPORT_DISMISSED;

    @JsonCreator
    public static NotificationType fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : NotificationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
