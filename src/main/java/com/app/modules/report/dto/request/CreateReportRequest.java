package com.app.modules.report.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload used to report a post, comment, user, story, or message")
public record CreateReportRequest(
        @NotNull @Schema(example = "POST") ReportType reportType,
        @NotNull @Schema(example = "SPAM") ReportReason reportReason,
        @NotNull @Schema(description = "Identifier of the reported entity") UUID entityId,
        @Size(max = 2000) @Schema(maxLength = 2000) String description) {}
