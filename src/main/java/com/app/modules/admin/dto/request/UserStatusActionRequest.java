package com.app.modules.admin.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.admin.enums.AdminActionType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for banning, unbanning, suspending, or unsuspending a user")
public record UserStatusActionRequest(
        @Schema(
                        description = "Requested user moderation action",
                        example = "suspend_user",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                AdminActionType actionType,
        @Schema(
                        description = "Audit reason for the status change",
                        example = "Repeated harassment violations",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason,
        @Schema(description = "Optional structured moderation context")
                Map<String, Object> metadata) {}
