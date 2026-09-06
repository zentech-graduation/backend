package com.app.common.security.config;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Binds CORS configuration from {@code app.cors.*}. {@code allowedOrigins} is a comma-separated
 * list of allowed origins, sourced from the {@code CORS_ALLOWED_ORIGINS} environment variable.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {

    /**
     * Parses {@link #allowedOrigins} into a trimmed, blank-filtered list.
     *
     * <p>Returns an empty list when unset or blank, which every CORS-facing surface (the REST
     * filter chain and both WebSocket STOMP endpoints) must treat as deny-all rather than falling
     * back to a permissive default.
     *
     * @return the parsed origin list, empty when {@link #allowedOrigins} is null or blank
     */
    public List<String> allowedOriginList() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return List.of();
        }
        return Stream.of(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
