package com.app.modules.report.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.report.enums.ReportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Moderation decision for a report")
public record UpdateReportStatusRequest(
        @NotNull @Schema(allowableValues = {"REVIEWING", "RESOLVED", "DISMISSED"})
                ReportStatus status,
        @Size(max = 2000) @Schema(maxLength = 2000) String resolutionNote) {}
