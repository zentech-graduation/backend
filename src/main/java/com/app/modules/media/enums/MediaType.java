package com.app.modules.media.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MediaType {
    IMAGE,
    VIDEO;

    @JsonCreator
    public static MediaType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return MediaType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
