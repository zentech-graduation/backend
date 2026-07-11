package com.app.modules.report.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** User-selectable reason for submitting a report. */
public enum ReportReason {
    SPAM,
    NUDITY,
    VIOLENCE,
    HATE_SPEECH,
    HARASSMENT,
    FALSE_INFORMATION,
    SCAM,
    OTHER;

    @JsonCreator
    public static ReportReason fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : ReportReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
