package com.app.modules.report.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.dto.response.ReportSummaryResponse;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for report submission and moderation review. */
@Tag(name = "Reports", description = "User reporting and moderator review lifecycle")
@RequestMapping(ApiConstants.Reports.ROOT)
public interface ReportApi {

    /** Submits a report for a live entity that is not owned by the authenticated caller. */
    @Operation(
            summary = "Submit a report",
            description =
                    "Creates one pending report for a post, comment, user, story, or message."
                            + " Duplicate and self-owned targets are rejected.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Report submitted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid payload or self-owned target",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target entity not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Entity already reported by the caller",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PostMapping
    ResponseEntity<ApiResponse<ReportResponse>> submitReport(
            @Valid @RequestBody CreateReportRequest request);

    /** Lists reports for moderators and administrators using optional filters. */
    @Operation(
            summary = "List reports",
            description =
                    "Returns a cursor-paginated moderation queue filtered by status and target"
                            + " type, ordered newest first. Requires MODERATOR or ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Report page returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping
    ResponseEntity<ApiResponse<CursorPageResponse<ReportSummaryResponse>>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit);

    /** Lists pending reports in FIFO order for moderator and administrator triage. */
    @Operation(
            summary = "List pending reports",
            description =
                    "Returns the pending moderation queue in FIFO order matching the"
                            + " pending_reports view. Requires MODERATOR or ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Pending report page returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Reports.PENDING)
    ResponseEntity<ApiResponse<CursorPageResponse<ReportSummaryResponse>>> getPendingReports(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit);

    /** Returns one report for moderator or administrator review. */
    @Operation(
            summary = "Get report details",
            description = "Returns one report by identifier. Requires MODERATOR or ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Report returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Report not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Reports.BY_ID)
    ResponseEntity<ApiResponse<ReportResponse>> getReport(@PathVariable("reportId") UUID reportId);

    /** Transitions a report through the moderation lifecycle. */
    @Operation(
            summary = "Update report status",
            description =
                    "Starts review or closes a report while recording reviewer metadata."
                            + " Resolution and dismissal require a note. Requires MODERATOR or"
                            + " ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Report status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Terminal transition lacks a resolution note",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Report not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Invalid lifecycle transition",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PatchMapping(ApiConstants.Reports.STATUS)
    ResponseEntity<ApiResponse<ReportResponse>> updateStatus(
            @PathVariable("reportId") UUID reportId,
            @Valid @RequestBody UpdateReportStatusRequest request);
}
