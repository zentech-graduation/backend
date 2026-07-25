package com.app.modules.users.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle status enumeration mapped to the Postgres {@code user_status} enum. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DEACTIVATED,
    BANNED;

    @JsonCreator
    public static UserStatus fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : UserStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
