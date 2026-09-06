package com.app.modules.report.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response containing a report and its review lifecycle metadata. */
@Schema(description = "Submitted report with review lifecycle metadata")
public record ReportResponse(
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
                        description = "Optional context supplied by the reporter",
                        example = "Repeated unsolicited promotional content",
                        nullable = true)
                String description,
        @Schema(
                        description = "Current review status",
                        example = "pending",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                ReportStatus status,
        @Schema(
                        description = "Moderator or administrator who reviewed the report",
                        example = "550e8400-e29b-41d4-a716-446655440003",
                        nullable = true)
                UUID reviewedBy,
        @Schema(
                        description = "Time the report most recently entered review",
                        example = "2026-07-11T10:30:00.000000Z",
                        nullable = true)
                OffsetDateTime reviewedAt,
        @Schema(
                        description = "Explanation recorded when the report was closed",
                        example = "Confirmed policy violation and removed the content",
                        nullable = true)
                String resolutionNote,
        @Schema(
                        description = "Report submission time",
                        example = "2026-07-11T10:21:03.522928Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                OffsetDateTime createdAt) {}
