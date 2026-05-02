package com.app.modules.auth.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        String tokenType,
        UserSummaryResponse user) {

    public static final String BEARER = "Bearer";
}
