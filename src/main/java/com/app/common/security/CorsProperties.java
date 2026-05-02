package com.app.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds CORS configuration from {@code app.cors.*}. {@code allowedOrigins} is a comma-separated
 * list of allowed origins, sourced from the {@code CORS_ALLOWED_ORIGINS} environment variable.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {}
