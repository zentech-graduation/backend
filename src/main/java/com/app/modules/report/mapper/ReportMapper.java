package com.app.modules.report.mapper;

import org.springframework.stereotype.Component;

import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.entity.Report;

@Component
public class ReportMapper {
    public ReportResponse toResponse(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporter().getId(),
                report.getReportType(),
                report.getReportReason(),
                report.getEntityId(),
                report.getDescription(),
                report.getStatus(),
                report.getReviewedBy() == null ? null : report.getReviewedBy().getId(),
                report.getReviewedAt(),
                report.getResolutionNote(),
                report.getCreatedAt());
    }
}
