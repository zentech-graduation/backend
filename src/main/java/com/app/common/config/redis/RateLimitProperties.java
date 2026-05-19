package com.app.common.config.redis;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sliding-window rate limit settings for sensitive auth endpoints. Bound from the {@code
 * app.rate-limit} namespace.
 *
 * <p>{@code endpointRules} maps a request path (e.g. {@code /api/v1/auth/register}) to a {@link
 * Rule} and is consulted by {@code AuthRateLimitFilter} to resolve per-path limits.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        Rule login, Rule forgotPassword, Rule resendVerification, Map<String, Rule> endpointRules) {

    public RateLimitProperties {
        endpointRules = endpointRules != null ? endpointRules : Collections.emptyMap();
    }

    public record Rule(int maxAttempts, long windowSeconds) {}
}
