package com.app.modules.report.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.dto.response.ReportSummaryResponse;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.mapper.ReportMapper;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.report.service.ReportService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CURSOR_SEPARATOR = "|";

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;

    public ReportServiceImpl(ReportRepository reportRepository, ReportMapper reportMapper) {
        this.reportRepository = reportRepository;
        this.reportMapper = reportMapper;
    }

    @Override
    @Transactional
    public ReportResponse submitReport(UUID reporterId, CreateReportRequest request) {
        UUID ownerId = validateEntityExists(request.reportType(), request.entityId());
        if (reporterId.equals(ownerId)) {
            throw new AppException(ApiErrorCode.REPORT_SELF_NOT_ALLOWED);
        }
        validateDuplicateReport(reporterId, request.reportType(), request.entityId());

        Report report =
                Report.builder()
                        .reporterId(reporterId)
                        .reportType(request.reportType())
                        .reportReason(request.reportReason())
                        .entityId(request.entityId())
                        .description(request.description())
                        .status(ReportStatus.PENDING)
                        .build();
        try {
            // The pre-check above cannot close the race between two concurrent submissions. The
            // unique index on (reporter_id, report_type, entity_id) is the authoritative guard;
            // flush here so the violation surfaces as a duplicate rather than a late 500.
            return reportMapper.toResponse(reportRepository.saveAndFlush(report));
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ApiErrorCode.REPORT_DUPLICATE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ReportSummaryResponse> listReports(
            ReportStatus status, ReportType reportType, String cursor, int size) {
        int pageSize = normalizeLimit(size);
        ReportCursor decoded = decodeCursor(cursor);
        int queryLimit = pageSize + 1;
        var reports =
                decoded.isEmpty()
                        ? findFirstReportPage(status, reportType, queryLimit)
                        : findReportPageAfterCursor(status, reportType, decoded, queryLimit);
        return toSummaryPage(reports, pageSize, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ReportSummaryResponse> getPendingReports(String cursor, int size) {
        int pageSize = normalizeLimit(size);
        ReportCursor decoded = decodeCursor(cursor);
        int queryLimit = pageSize + 1;
        var reports =
                decoded.isEmpty()
                        ? reportRepository.findFirstReportsByStatusOldestFirst(
                                ReportStatus.PENDING, queryLimit)
                        : reportRepository.findAllByStatusAfterCursor(
                                ReportStatus.PENDING,
                                decoded.createdAt(),
                                decoded.id(),
                                queryLimit);
        return toSummaryPage(reports, pageSize, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId) {
        return reportMapper.toResponse(findReport(reportId));
    }

    @Override
    @Transactional
    public ReportResponse updateStatus(
            UUID reportId, UUID reviewerId, UpdateReportStatusRequest request) {
        Report report = findReport(reportId);
        validateTransition(report.getStatus(), request.status());
        validateResolutionNote(request.status(), request.resolutionNote());

        report.setStatus(request.status());
        report.setReviewedBy(reviewerId);
        report.setReviewedAt(OffsetDateTime.now());
        report.setResolutionNote(
                isTerminal(request.status()) ? request.resolutionNote().trim() : null);
        return reportMapper.toResponse(reportRepository.save(report));
    }

    private UUID validateEntityExists(ReportType reportType, UUID entityId) {
        return reportRepository
                .findOwnerId(reportType, entityId)
                .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_TARGET_NOT_FOUND));
    }

    private void validateDuplicateReport(UUID reporterId, ReportType reportType, UUID entityId) {
        if (reportRepository.existsByReporterIdAndReportTypeAndEntityId(
                reporterId, reportType, entityId)) {
            throw new AppException(ApiErrorCode.REPORT_DUPLICATE);
        }
    }

    private Report findReport(UUID reportId) {
        return reportRepository
                .findById(reportId)
                .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_NOT_FOUND));
    }

    private void validateTransition(ReportStatus current, ReportStatus target) {
        boolean valid =
                switch (current) {
                    case PENDING ->
                            target == ReportStatus.REVIEWING
                                    || target == ReportStatus.RESOLVED
                                    || target == ReportStatus.DISMISSED;
                    case REVIEWING ->
                            target == ReportStatus.RESOLVED || target == ReportStatus.DISMISSED;
                    case RESOLVED, DISMISSED -> false;
                };
        if (!valid) {
            throw new AppException(ApiErrorCode.REPORT_INVALID_TRANSITION);
        }
    }

    private void validateResolutionNote(ReportStatus target, String resolutionNote) {
        if (isTerminal(target) && !StringUtils.hasText(resolutionNote)) {
            throw new AppException(ApiErrorCode.REPORT_RESOLUTION_NOTE_REQUIRED);
        }
    }

    private boolean isTerminal(ReportStatus status) {
        return status == ReportStatus.RESOLVED || status == ReportStatus.DISMISSED;
    }

    private java.util.List<Report> findFirstReportPage(
            ReportStatus status, ReportType reportType, int limit) {
        if (status != null && reportType != null) {
            return reportRepository.findFirstReportsByStatusAndReportType(
                    status, reportType, limit);
        }
        if (status != null) {
            return reportRepository.findFirstReportsByStatus(status, limit);
        }
        if (reportType != null) {
            return reportRepository.findFirstReportsByReportType(reportType, limit);
        }
        return reportRepository.findFirstReports(limit);
    }

    private java.util.List<Report> findReportPageAfterCursor(
            ReportStatus status, ReportType reportType, ReportCursor cursor, int limit) {
        if (status != null && reportType != null) {
            return reportRepository.findAllByStatusAndReportTypeBeforeCursor(
                    status, reportType, cursor.createdAt(), cursor.id(), limit);
        }
        if (status != null) {
            return reportRepository.findAllByStatusBeforeCursor(
                    status, cursor.createdAt(), cursor.id(), limit);
        }
        if (reportType != null) {
            return reportRepository.findAllByReportTypeBeforeCursor(
                    reportType, cursor.createdAt(), cursor.id(), limit);
        }
        return reportRepository.findAllBeforeCursor(cursor.createdAt(), cursor.id(), limit);
    }

    private CursorPageResponse<ReportSummaryResponse> toSummaryPage(
            java.util.List<Report> reports, int pageSize, boolean hasPreviousPage) {
        boolean hasNextPage = reports.size() > pageSize;
        java.util.List<Report> pageReports = hasNextPage ? reports.subList(0, pageSize) : reports;
        if (pageReports.isEmpty()) {
            return CursorPageResponse.<ReportSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .pageInfo(
                            CursorPageResponse.PageInfo.builder()
                                    .hasNextPage(false)
                                    .hasPreviousPage(hasPreviousPage)
                                    .startCursor(null)
                                    .endCursor(null)
                                    .build())
                    .build();
        }
        java.util.List<ReportSummaryResponse> content =
                reportMapper.toSummaryResponseList(pageReports);
        return CursorPageResponse.<ReportSummaryResponse>builder()
                .content(content)
                .pageInfo(
                        CursorPageResponse.PageInfo.builder()
                                .hasNextPage(hasNextPage)
                                .hasPreviousPage(hasPreviousPage)
                                .startCursor(encodeCursor(pageReports.get(0)))
                                .endCursor(encodeCursor(pageReports.get(pageReports.size() - 1)))
                                .build())
                .build();
    }

    private int normalizeLimit(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String encodeCursor(Report report) {
        String raw = report.getCreatedAt() + CURSOR_SEPARATOR + report.getId();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ReportCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new ReportCursor(null, null);
        }
        try {
            String raw = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Cursor must contain createdAt and id");
            }
            return new ReportCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }

    private record ReportCursor(OffsetDateTime createdAt, UUID id) {

        boolean isEmpty() {
            return createdAt == null || id == null;
        }
    }
}
