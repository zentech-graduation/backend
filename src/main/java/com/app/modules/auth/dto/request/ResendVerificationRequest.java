package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload to request a new email verification link")
public record ResendVerificationRequest(
        @Schema(
                        description = "Email address of the unverified account",
                        example = "john@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Email
                String email) {}
