package com.app.modules.report.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Review lifecycle of a submitted report. */
public enum ReportStatus {
    PENDING,
    REVIEWING,
    RESOLVED,
    DISMISSED;

    @JsonCreator
    public static ReportStatus fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : ReportStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
