package com.app.modules.report.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.app.common.response.PageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

public interface ReportService {
    ReportResponse submit(UUID reporterId, CreateReportRequest request);

    PageResponse<ReportResponse> findAll(ReportStatus status, ReportType type, Pageable pageable);

    ReportResponse findById(UUID reportId);

    ReportResponse transitionStatus(
            UUID reportId, UUID reviewerId, UpdateReportStatusRequest request);
}
