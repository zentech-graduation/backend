package com.app.common.config.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds top-level application configuration from {@code app.*}.
 *
 * <p>Currently exposes the public base URL used to compose absolute links in outbound emails (e.g.
 * verification, password reset). Driven by {@code APP_BASE_URL} environment variable.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl) {}
