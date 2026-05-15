package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credentials for email/password login")
public record LoginRequest(
        @Schema(
                        description = "Registered email address",
                        example = "john@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Email
                String email,
        @Schema(
                        description = "Account password",
                        example = "S3cur3P@ssword",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String password) {}
