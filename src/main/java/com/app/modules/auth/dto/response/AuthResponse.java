package com.app.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authentication token pair with basic user information returned on login, register, and refresh.
 */
@Schema(description = "Access/refresh token pair with basic user information")
public record AuthResponse(
        @Schema(
                        description =
                                "Short-lived JWT access token to be sent as Bearer in Authorization header",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String accessToken,
        @Schema(
                        description = "Opaque refresh token used to rotate the session",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String refreshToken,
        @Schema(
                        description = "Access token lifetime in seconds from the time of issuance",
                        example = "900")
                long accessTokenExpiresIn,
        @Schema(description = "Token scheme; always \"Bearer\"", example = "Bearer")
                String tokenType,
        @Schema(description = "Summary of the authenticated user") UserSummaryResponse user) {

    public static final String BEARER = "Bearer";
}
