package com.app.modules.auth.service;

import java.util.UUID;

/**
 * Manages short-lived opaque exchange codes that bridge the OAuth2 callback redirect and the
 * back-channel token exchange.
 *
 * <p>After a successful OAuth2 authentication the success handler stores the authenticated user's
 * ID under a randomly generated code in Redis (TTL 120 s) and redirects the browser to the frontend
 * with only the code in the URL. The frontend immediately calls the exchange endpoint to redeem the
 * code for an access/refresh token pair.
 */
public interface OAuth2ExchangeCodeService {

    /**
     * Generates a cryptographically random 64-character hex exchange code, stores it in Redis under
     * {@code auth:oauth2:exchange:{code}} with a 120-second TTL, and returns the raw code.
     *
     * @param userId the authenticated user whose session will be issued on redemption
     * @return the raw hex-encoded exchange code
     */
    String storeExchangeCode(UUID userId);

    /**
     * Atomically consumes the exchange code from Redis and returns the owning user ID.
     *
     * <p>The code is deleted on first consumption so it cannot be redeemed twice.
     *
     * @param code the raw hex-encoded exchange code presented by the frontend
     * @return the user ID stored when the code was issued
     * @throws com.app.common.exception.AppException with {@link
     *     com.app.common.enums.ApiErrorCode#AUTH_OAUTH2_EXCHANGE_CODE_INVALID} when the code is
     *     absent or expired
     */
    UUID consumeExchangeCode(String code);
}
