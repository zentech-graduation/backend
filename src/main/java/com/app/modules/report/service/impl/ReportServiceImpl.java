package com.app.modules.report.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.PageResponse;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.mapper.ReportMapper;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.report.service.ReportService;

@Service
public class ReportServiceImpl implements ReportService {
    private static final List<ReportStatus> ACTIVE_STATUSES =
            List.of(ReportStatus.PENDING, ReportStatus.REVIEWING);
    private static final Map<ReportType, String> TARGET_TABLES =
            Map.of(
                    ReportType.POST, "posts",
                    ReportType.COMMENT, "comments",
                    ReportType.USER, "users",
                    ReportType.STORY, "stories",
                    ReportType.MESSAGE, "messages");

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportMapper reportMapper;
    private final EntityManager entityManager;

    public ReportServiceImpl(
            ReportRepository reportRepository,
            UserRepository userRepository,
            ReportMapper reportMapper,
            EntityManager entityManager) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.reportMapper = reportMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ReportResponse submit(UUID reporterId, CreateReportRequest request) {
        User reporter =
                userRepository
                        .findByIdAndDeletedAtIsNull(reporterId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.UNAUTHORIZED));
        validateEntityExists(request.reportType(), request.entityId());
        validateNoDuplicate(reporterId, request.reportType(), request.entityId());
        Report report =
                Report.builder()
                        .reporter(reporter)
                        .reportType(request.reportType())
                        .reportReason(request.reportReason())
                        .entityId(request.entityId())
                        .description(request.description())
                        .status(ReportStatus.PENDING)
                        .build();
        return reportMapper.toResponse(reportRepository.save(report));
    }

    void validateEntityExists(ReportType type, UUID entityId) {
        String table = TARGET_TABLES.get(type);
        Number count =
                (Number)
                        entityManager
                                .createNativeQuery(
                                        "select count(*) from " + table + " where id = :id")
                                .setParameter("id", entityId)
                                .getSingleResult();
        if (count.longValue() == 0) {
            throw new AppException(ApiErrorCode.REPORT_TARGET_NOT_FOUND);
        }
    }

    void validateNoDuplicate(UUID reporterId, ReportType type, UUID entityId) {
        if (reportRepository.existsByReporterIdAndReportTypeAndEntityIdAndStatusIn(
                reporterId, type, entityId, ACTIVE_STATUSES)) {
            throw new AppException(ApiErrorCode.REPORT_DUPLICATE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> findAll(
            ReportStatus status, ReportType type, Pageable pageable) {
        Page<ReportResponse> page =
                reportRepository
                        .findAllFiltered(status, type, pageable)
                        .map(reportMapper::toResponse);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse findById(UUID reportId) {
        return reportMapper.toResponse(getReport(reportId));
    }

    @Override
    @Transactional
    public ReportResponse transitionStatus(
            UUID reportId, UUID reviewerId, UpdateReportStatusRequest request) {
        Report report = getReport(reportId);
        User reviewer =
                userRepository
                        .findByIdAndDeletedAtIsNull(reviewerId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.UNAUTHORIZED));
        if (!isAllowed(report.getStatus(), request.status())) {
            throw new AppException(ApiErrorCode.REPORT_INVALID_TRANSITION);
        }
        report.setStatus(request.status());
        report.setReviewedBy(reviewer);
        report.setReviewedAt(OffsetDateTime.now());
        if (request.status() == ReportStatus.RESOLVED
                || request.status() == ReportStatus.DISMISSED) {
            report.setResolutionNote(request.resolutionNote());
        }
        return reportMapper.toResponse(reportRepository.save(report));
    }

    private Report getReport(UUID id) {
        return reportRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_NOT_FOUND));
    }

    private boolean isAllowed(ReportStatus current, ReportStatus next) {
        if (current == next) return false;
        return switch (current) {
            case PENDING ->
                    next == ReportStatus.REVIEWING
                            || next == ReportStatus.RESOLVED
                            || next == ReportStatus.DISMISSED;
            case REVIEWING -> next == ReportStatus.RESOLVED || next == ReportStatus.DISMISSED;
            case RESOLVED, DISMISSED -> false;
        };
    }
}
