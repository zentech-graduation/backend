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
        @Schema(description = "Report identifier") UUID id,
        @Schema(description = "Reporter user identifier") UUID reporterId,
        @Schema(description = "Type of reported entity") ReportType reportType,
        @Schema(description = "Reason selected by the reporter") ReportReason reportReason,
        @Schema(description = "Reported entity identifier") UUID entityId,
        @Schema(description = "Optional context supplied by the reporter") String description,
        @Schema(description = "Current review status") ReportStatus status,
        @Schema(description = "Moderator or administrator who reviewed the report") UUID reviewedBy,
        @Schema(description = "Time the report most recently entered review")
                OffsetDateTime reviewedAt,
        @Schema(description = "Explanation recorded when the report was closed")
                String resolutionNote,
        @Schema(description = "Report submission time") OffsetDateTime createdAt) {}
