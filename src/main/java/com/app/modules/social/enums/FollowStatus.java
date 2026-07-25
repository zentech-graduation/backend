package com.app.modules.social.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FollowStatus {
    PENDING,
    ACCEPTED;

    @JsonCreator
    public static FollowStatus fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : FollowStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
