package com.app.modules.report.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lightweight report projection for moderation queues. */
@Schema(description = "Summary of a submitted report for moderation queues")
public record ReportSummaryResponse(
        @Schema(
                        description = "Report identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID id,
        @Schema(
                        description = "Reporter user identifier",
                        example = "550e8400-e29b-41d4-a716-446655440001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID reporterId,
        @Schema(
                        description = "Type of reported entity",
                        example = "post",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                ReportType reportType,
        @Schema(
                        description = "Reason selected by the reporter",
                        example = "spam",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                ReportReason reportReason,
        @Schema(
                        description = "Reported entity identifier",
                        example = "550e8400-e29b-41d4-a716-446655440002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID entityId,
        @Schema(
                        description = "Current review status",
                        example = "pending",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                ReportStatus status,
        @Schema(
                        description = "Report submission time",
                        example = "2026-07-11T10:21:03.522928Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                OffsetDateTime createdAt) {}
