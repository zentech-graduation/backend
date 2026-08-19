package com.app.modules.report.service;

import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.dto.response.ReportSummaryResponse;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.users.enums.UserRole;

public interface ReportService {

    /**
     * Submits a report after validating target existence, ownership, and uniqueness.
     *
     * @param reporterId authenticated reporter identifier
     * @param request report target and reason
     * @return persisted pending report
     * @throws AppException when the target is missing, self-owned, or already reported
     */
    ReportResponse submitReport(UUID reporterId, CreateReportRequest request);

    /**
     * Lists reports for moderation with optional status and target-type filters.
     *
     * <p>A moderator's queue is the open part of the lifecycle: pending and reviewing. Asking for a
     * closed or escalated status returns an empty page rather than an error, which is the stealth
     * model this codebase uses elsewhere and which avoids confirming that rows exist behind the
     * filter.
     *
     * <p>An administrator's queue is unchanged and includes escalated reports.
     *
     * <p>The cursor is scoped per role. Filtering by role without doing so would let a moderator
     * replay an administrator's cursor and page into rows its own listing never produces.
     *
     * <p>The role is a parameter rather than a lookup so this module keeps importing no other. It
     * is the caller's real role either way: the principal it comes from is rebuilt from the account
     * row on every request, not from a token claim.
     *
     * @param actorRole role of the caller, which decides the visible statuses
     * @param status optional lifecycle status filter
     * @param reportType optional target-type filter
     * @param cursor opaque cursor from the prior page
     * @param size requested page size
     * @return matching report page
     */
    CursorPageResponse<ReportSummaryResponse> listReports(
            UserRole actorRole,
            ReportStatus status,
            ReportType reportType,
            String cursor,
            int size);

    /**
     * Lists pending reports in FIFO order for moderator triage.
     *
     * @param cursor opaque cursor from the prior page
     * @param size requested page size
     * @return pending report page ordered oldest first
     */
    CursorPageResponse<ReportSummaryResponse> getPendingReports(String cursor, int size);

    /**
     * Returns a report by identifier for moderation review.
     *
     * @param reportId report identifier
     * @return report details
     * @throws AppException when the report does not exist
     */
    ReportResponse getReport(UUID reportId);

    /**
     * Applies a valid moderation lifecycle transition and records reviewer metadata.
     *
     * @param reportId report identifier
     * @param reviewerId authenticated moderator or administrator identifier
     * @param request target status and optional resolution note
     * @return updated report
     * @throws AppException when the report is missing, the transition is invalid, or a terminal
     *     transition lacks a resolution note
     */
    ReportResponse updateStatus(UUID reportId, UUID reviewerId, UpdateReportStatusRequest request);
}
