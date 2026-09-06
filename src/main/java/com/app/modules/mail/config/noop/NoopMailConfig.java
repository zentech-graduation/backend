package com.app.modules.mail.config.noop;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the {@code noop} mail transport.
 *
 * <p>Declares the {@link SentMailRecorder} the {@code noop} {@code MailSender} implementation
 * writes to instead of reaching a real provider. Exists only so the test phase can run the full
 * application context without ever constructing a network-capable mail client.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "noop")
public class NoopMailConfig {

    @Bean
    public SentMailRecorder sentMailRecorder() {
        return new SentMailRecorder();
    }
}
