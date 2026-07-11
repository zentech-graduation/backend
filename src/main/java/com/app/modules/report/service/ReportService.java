package com.app.modules.report.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.app.common.exception.AppException;
import com.app.common.response.PageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

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
     * @param status optional lifecycle status filter
     * @param reportType optional target-type filter
     * @param pageable offset pagination and ordering
     * @return matching report page
     */
    PageResponse<ReportResponse> listReports(
            ReportStatus status, ReportType reportType, Pageable pageable);

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
