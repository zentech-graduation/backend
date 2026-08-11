package com.app.modules.mail.config.resend;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds Resend transport configuration from {@code app.mail.resend.*}.
 *
 * <p>Holds the provider credential only. Sender identity and application metadata are
 * transport-neutral and live in {@code app.mail.*}; never inline secrets here.
 */
@ConfigurationProperties(prefix = "app.mail.resend")
@Getter
@Setter
public class ResendProperties {

    private String apiKey;
}
