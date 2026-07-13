package com.app.modules.admin.dto.request;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.admin.enums.AdminActionType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for removing or restoring a post")
public record PostModerationActionRequest(
        @Schema(
                        description = "Requested post moderation action",
                        example = "remove_post",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                AdminActionType actionType,
        @Schema(
                        description = "Audit reason for the moderation action",
                        example = "Post violates the violence policy",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason,
        @Schema(description = "Optional report that prompted this action") UUID reportId,
        @Schema(description = "Optional structured moderation context")
                Map<String, Object> metadata) {}
