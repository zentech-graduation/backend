package com.app.modules.admin.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Bucket widths persisted in the PostgreSQL {@code stat_granularity} enum. */
public enum StatGranularity {
    HALF_HOUR,
    DAY;

    @JsonCreator
    public static StatGranularity fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : StatGranularity.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
