package com.app.modules.mail.config.smtp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds SMTP transport configuration from {@code app.mail.smtp.*}.
 *
 * <p>Defaults target a local Mailpit sink, which listens on 1025 and accepts unauthenticated
 * plaintext submission. Every field has a default so the transport wiring binds without operator
 * input in development and in tests.
 */
@ConfigurationProperties(prefix = "app.mail.smtp")
@Getter
@Setter
public class SmtpProperties {

    private String host = "localhost";
    private int port = 1025;
    private String username;
    private String password;
    private boolean auth = false;
    private boolean starttls = false;
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Duration writeTimeout = Duration.ofSeconds(10);
}
