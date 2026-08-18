package com.app.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.users.enums.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;

/** Requested role for a target account, with the audit reason for the change. */
@Schema(description = "Requested role for a target account")
public record AdminRoleChangeRequest(
        @Schema(
                        description = "Role to assign to the target account",
                        example = "moderator",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                UserRole role,
        @Schema(
                        description = "Human-readable reason for the role change",
                        example = "Promoted after moderation training",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason) {}
