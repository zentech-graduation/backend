package com.app.modules.mail.config.resend;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "resend")
public class ResendMailConfig {

    @Bean
    public Resend resend(ResendProperties resendProperties) {
        if (!StringUtils.hasText(resendProperties.getApiKey())) {
            throw new IllegalStateException(
                    "The resend mail transport is selected but app.mail.resend.api-key is blank."
                            + " Set RESEND_API_KEY, or select another transport.");
        }
        return new Resend(resendProperties.getApiKey());
    }
}
