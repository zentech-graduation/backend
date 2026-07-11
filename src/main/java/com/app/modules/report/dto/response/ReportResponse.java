package com.app.modules.report.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Report details and moderation state")
public record ReportResponse(
        UUID id,
        UUID reporterId,
        ReportType reportType,
        ReportReason reportReason,
        UUID entityId,
        String description,
        ReportStatus status,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        String resolutionNote,
        OffsetDateTime createdAt) {}
