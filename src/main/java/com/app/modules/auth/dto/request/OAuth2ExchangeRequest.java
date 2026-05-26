package com.app.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for redeeming a short-lived OAuth2 exchange code for a token pair. */
@Schema(description = "OAuth2 exchange code redemption request")
public record OAuth2ExchangeRequest(
        @Schema(
                        description =
                                "Short-lived opaque exchange code issued by the OAuth2 success"
                                        + " handler and delivered to the frontend via URL parameter",
                        example =
                                "a3f2c1d4e5b6a7f8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String code) {}
