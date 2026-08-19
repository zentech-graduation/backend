package com.app.modules.report.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Review lifecycle of a submitted report. */
public enum ReportStatus {
    PENDING,
    REVIEWING,
    RESOLVED,
    DISMISSED,
    /**
     * Handed up to an administrator, and out of the moderator queue.
     *
     * <p>Not a terminal state, but there is deliberately no way back to {@code PENDING} or {@code
     * REVIEWING}: escalation is a moderator saying it should not decide this one, and letting the
     * report drop back into the queue it just left would make that meaningless.
     */
    ESCALATED;

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
