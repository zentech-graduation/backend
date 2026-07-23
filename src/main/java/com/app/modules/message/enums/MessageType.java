package com.app.modules.message.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Content kind of a message, mirroring the Postgres {@code message_type} enum. */
public enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    POST_SHARE,
    STORY_SHARE;

    @JsonCreator
    public static MessageType fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : MessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
