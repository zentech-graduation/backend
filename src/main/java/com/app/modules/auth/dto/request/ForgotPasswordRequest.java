package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload to trigger a password-reset email")
public record ForgotPasswordRequest(
        @Schema(
                        description = "Email address associated with the account",
                        example = "john@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Email
                String email) {}
