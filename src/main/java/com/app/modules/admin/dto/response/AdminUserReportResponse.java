package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** One report filed against an account, as shown on the administrative detail view. */
@Schema(description = "One report filed against an account")
public record AdminUserReportResponse(
        @Schema(description = "Report identifier") UUID id,
        @Schema(description = "Account that filed the report") UUID reporterId,
        @Schema(description = "Selected reason", example = "harassment") ReportReason reportReason,
        @Schema(description = "Triage status", example = "pending") ReportStatus status,
        @Schema(description = "Submission timestamp") OffsetDateTime createdAt) {}
