package com.app.common.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sliding-window rate limit settings for sensitive auth endpoints. Bound from the {@code
 * app.rate-limit} namespace.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Rule login, Rule forgotPassword, Rule resendVerification) {

    public record Rule(int maxAttempts, long windowSeconds) {}
}
