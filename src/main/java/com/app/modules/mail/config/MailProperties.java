package com.app.modules.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds outbound mail configuration from {@code app.mail.*}.
 *
 * <p>Holds sender identity and application metadata used by every transactional email. Values
 * originate from environment variables via {@code application.yaml} placeholders; never inline
 * secrets here.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    private String fromAddress;
    private String fromName;
    private String appName;
    private String frontendBaseUrl;
    private String resetPasswordPath = "/reset-password";
    private String verifyEmailPath = "/verify-email";
}
