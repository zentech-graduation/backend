package com.app.modules.report.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.PageResponse;
import com.app.common.security.SecurityUtils;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.service.ReportService;

@RestController
@RequestMapping(ApiConstants.Reports.ROOT)
public class ReportController implements ReportApi {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> submit(
            @Valid @RequestBody CreateReportRequest request) {
        ReportResponse result = reportService.submit(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED, result));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> findAll(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportType type,
            Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK, reportService.findAll(status, type, pageable)));
    }

    @Override
    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<ReportResponse>> findById(@PathVariable UUID reportId) {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, reportService.findById(reportId)));
    }

    @Override
    @PatchMapping("/{reportId}/status")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<ReportResponse>> updateStatus(
            @PathVariable UUID reportId, @Valid @RequestBody UpdateReportStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.OK,
                        reportService.transitionStatus(
                                reportId, SecurityUtils.getCurrentUserId(), request)));
    }
}
