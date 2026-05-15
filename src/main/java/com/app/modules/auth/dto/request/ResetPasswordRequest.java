package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload to complete a password reset using the one-time token")
public record ResetPasswordRequest(
        @Schema(
                        description = "One-time reset token from the password-reset email link",
                        example = "a1b2c3d4e5f6...",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String token,
        @Schema(
                        description = "New password to set; 8–128 characters",
                        example = "N3wS3cur3P@ss",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(min = 8, max = 128)
                String newPassword) {}
