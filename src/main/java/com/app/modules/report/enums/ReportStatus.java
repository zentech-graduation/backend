package com.app.modules.report.enums;

import java.util.List;
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

    /**
     * The open part of the lifecycle, and the whole of a moderator's queue.
     *
     * <p>Declared on the enum rather than in the service that reads it because it is not only the
     * service that knows it: {@code idx_reports_open_queue} is a partial index whose predicate
     * names exactly these values, and a status added here without being added there would leave the
     * queue silently back on a sequential scan of every report ever filed. {@code
     * ReportQueueIndexIT} asserts the two agree.
     */
    public static final List<ReportStatus> OPEN_QUEUE = List.of(PENDING, REVIEWING);

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
