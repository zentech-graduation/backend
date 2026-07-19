package com.app.modules.story.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Media kind of a story, mirroring the Postgres {@code story_type} enum. */
public enum StoryType {
    IMAGE,
    VIDEO;

    @JsonCreator
    public static StoryType fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : StoryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
