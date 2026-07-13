package com.app.modules.admin.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.admin.enums.AdminActionType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for resolving or dismissing a report with an audit event")
public record ReportResolutionActionRequest(
        @Schema(
                        description = "Requested report closure action",
                        example = "resolve_report",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                AdminActionType actionType,
        @Schema(
                        description = "Resolution note stored on both the report and audit event",
                        example = "Violation confirmed and content removed",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason,
        @Schema(description = "Optional structured moderation context")
                Map<String, Object> metadata) {}
