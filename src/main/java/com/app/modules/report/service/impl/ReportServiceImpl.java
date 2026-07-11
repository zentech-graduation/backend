package com.app.modules.report.service.impl;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.PageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
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
        return reportMapper.toResponse(reportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> listReports(
            ReportStatus status, ReportType reportType, Pageable pageable) {
        Page<Report> entities;
        if (status != null && reportType != null) {
            entities = reportRepository.findAllByStatusAndReportType(status, reportType, pageable);
        } else if (status != null) {
            entities = reportRepository.findAllByStatus(status, pageable);
        } else if (reportType != null) {
            entities = reportRepository.findAllByReportType(reportType, pageable);
        } else {
            entities = reportRepository.findAll(pageable);
        }
        Page<ReportResponse> reports = entities.map(reportMapper::toResponse);
        return PageResponse.from(reports);
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
}
