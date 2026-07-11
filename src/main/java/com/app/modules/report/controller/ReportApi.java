package com.app.modules.report.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.response.ApiResponse;
import com.app.common.response.PageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Reports", description = "Report submission and moderation workflow")
@SecurityRequirement(name = "bearerAuth")
public interface ReportApi {
    @Operation(summary = "Submit a report")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Report submitted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Duplicate report")
    })
    @PostMapping
    ResponseEntity<ApiResponse<ReportResponse>> submit(
            @Valid @RequestBody CreateReportRequest request);

    @Operation(summary = "List reports", description = "Moderator or administrator only")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @GetMapping
    ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> findAll(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportType type,
            @Parameter(hidden = true) Pageable pageable);

    @Operation(summary = "Get report details", description = "Moderator or administrator only")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @GetMapping("/{reportId}")
    ResponseEntity<ApiResponse<ReportResponse>> findById(@PathVariable UUID reportId);

    @Operation(
            summary = "Transition report status",
            description = "Moderator or administrator only")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @PatchMapping("/{reportId}/status")
    ResponseEntity<ApiResponse<ReportResponse>> updateStatus(
            @PathVariable UUID reportId, @Valid @RequestBody UpdateReportStatusRequest request);
}
