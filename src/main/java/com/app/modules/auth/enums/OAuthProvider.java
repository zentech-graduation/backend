package com.app.modules.auth.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Identifier for the third-party identity providers supported by the app. */
public enum OAuthProvider {
    GOOGLE,
    FACEBOOK,
    APPLE;

    @JsonCreator
    public static OAuthProvider fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : OAuthProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
