package com.app.modules.post.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PostType {
    IMAGE,
    VIDEO,
    CAROUSEL,
    TEXT;

    @JsonCreator
    public static PostType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return PostType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
