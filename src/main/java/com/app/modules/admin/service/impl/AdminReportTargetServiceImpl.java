package com.app.modules.admin.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.admin.dto.response.AdminReportTargetResponse;
import com.app.modules.admin.repository.AdminReportTargetRepository;
import com.app.modules.admin.service.AdminReportTargetService;
import com.app.modules.report.entity.Report;
import com.app.modules.report.repository.ReportRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdminReportTargetServiceImpl implements AdminReportTargetService {

    private final ReportRepository reportRepository;
    private final AdminReportTargetRepository adminReportTargetRepository;

    public AdminReportTargetServiceImpl(
            ReportRepository reportRepository,
            AdminReportTargetRepository adminReportTargetRepository) {
        this.reportRepository = reportRepository;
        this.adminReportTargetRepository = adminReportTargetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportTargetResponse getReportTarget(UUID actorId, UUID reportId) {
        Report report =
                reportRepository
                        .findById(reportId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_NOT_FOUND));
        AdminReportTargetResponse target =
                adminReportTargetRepository
                        .find(report.getReportType(), report.getEntityId())
                        // Gone rather than not-found: the report is real and the moderator reached
                        // it legitimately, and the entity_id column carries no foreign key, so a
                        // hard-deleted target leaves the report pointing at nothing.
                        .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_TARGET_GONE));
        // Logged, not audited. This is a read that happens many times per report, and an
        // admin_actions row for each would dilute a table that exists to record state changes.
        log.info(
                "Report target reviewed: reportId={}, actorId={}, entityId={}",
                reportId,
                actorId,
                report.getEntityId());
        return target;
    }
}
