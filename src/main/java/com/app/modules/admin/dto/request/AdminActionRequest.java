package com.app.modules.admin.dto.request;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audit context for one explicit moderation operation")
public record AdminActionRequest(
        @Schema(
                        description = "Human-readable reason for the moderation operation",
                        example = "Repeated harassment violations",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason,
        @Schema(description = "Optional report that prompted the moderation operation")
                UUID reportId,
        @Schema(description = "Optional structured moderation context") @Size(max = 20)
                Map<String, Object> metadata) {}
