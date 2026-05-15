package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload carrying the refresh token for rotation or logout")
public record RefreshRequest(
        @Schema(
                        description = "Opaque refresh token issued at login or a prior rotation",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String refreshToken) {}
