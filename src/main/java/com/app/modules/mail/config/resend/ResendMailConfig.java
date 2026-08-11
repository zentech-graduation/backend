package com.app.modules.mail.config.resend;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.resend.Resend;

/**
 * Wiring for the Resend mail transport.
 *
 * <p>Declares the {@link Resend} client used by the Resend {@code MailSender} implementation. The
 * credential has no default, so this configuration is registered only when Resend is the selected
 * transport.
 */
@Configuration
@EnableConfigurationProperties(ResendProperties.class)
public class ResendMailConfig {

    @Bean
    public Resend resend(ResendProperties resendProperties) {
        return new Resend(resendProperties.getApiKey());
    }
}
