package com.app.modules.users.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Role enumeration mapped to the Postgres {@code user_role} enum. */
public enum UserRole {
    USER,
    MODERATOR,
    ADMIN;

    @JsonCreator
    public static UserRole fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
