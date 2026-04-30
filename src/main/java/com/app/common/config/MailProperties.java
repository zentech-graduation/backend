package com.app.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds outbound mail configuration from {@code app.mail.*}.
 *
 * <p>Holds sender identity used by every transactional email. Values originate from environment
 * variables via {@code application.yaml} placeholders; never inline secrets here.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String fromAddress, String fromName) {}
