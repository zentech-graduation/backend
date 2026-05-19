package com.app.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds JWT configuration from {@code app.jwt.*}.
 *
 * @param secret HMAC-SHA256 signing key (must be at least 32 characters)
 * @param issuer value placed in the {@code iss} claim
 * @param audience value placed in the {@code aud} claim
 * @param accessTokenTtl access token lifetime in seconds
 * @param refreshTokenTtl refresh token lifetime in seconds
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret, String issuer, String audience, long accessTokenTtl, long refreshTokenTtl) {}
