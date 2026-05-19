package com.app.common.config.security;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds security-related configuration from {@code app.security.*}.
 *
 * <p>{@code trustedProxyCidrs}: IP addresses or CIDR ranges of proxies whose {@code
 * X-Forwarded-For} header values are trusted for real-IP extraction. Defaults to an empty list (no
 * proxy trusted). {@code maxLoginBodyBytes}: upper bound on the cached request body size for the
 * login endpoint rate-limiter.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull @DefaultValue List<String> trustedProxyCidrs,
        @PositiveOrZero @DefaultValue("2048") int maxLoginBodyBytes) {}
