package com.app.common.seed.model;

/**
 * Parsed content of one entry in {@code moderation_cases.json}'s {@code supplementary_reports}
 * array: a standalone {@code reports} row not attached to any of the six narrative cases.
 */
public record ModerationSupplementaryReport(
        String at,
        String reporter,
        String targetUsername,
        String reportType,
        String reportReason,
        String reportStatus,
        String note) {}
