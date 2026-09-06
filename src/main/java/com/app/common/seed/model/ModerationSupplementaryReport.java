package com.app.common.seed.model;

/**
 * Parsed content of one entry in {@code moderation_cases.json}'s {@code supplementary_reports}
 * array: a standalone {@code reports} row not attached to any of the six narrative cases.
 *
 * <p>{@code id} is optional and only present on entries a {@link ModerationSupplementaryAction}
 * references via {@code target_report_ref} (e.g. a {@code dismiss_report} action naming the exact
 * report it closed) - most entries carry no {@code id} since nothing needs to address them
 * individually.
 */
public record ModerationSupplementaryReport(
        String id,
        String at,
        String reporter,
        String targetUsername,
        String reportType,
        String reportReason,
        String reportStatus,
        String note) {}
