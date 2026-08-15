package com.app.modules.mail.config.smtp;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * Wiring for the SMTP mail transport.
 *
 * <p>Declares the {@link JavaMailSender} used by the SMTP {@code MailSender} implementation. The
 * client opens no connection until a message is sent, so registering it costs nothing at startup.
 */
@Configuration
@EnableConfigurationProperties(SmtpProperties.class)
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "smtp")
public class SmtpMailConfig {

    @Bean
    public JavaMailSender javaMailSender(SmtpProperties smtpProperties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtpProperties.getHost());
        sender.setPort(smtpProperties.getPort());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (StringUtils.hasText(smtpProperties.getUsername())) {
            sender.setUsername(smtpProperties.getUsername());
        }
        if (StringUtils.hasText(smtpProperties.getPassword())) {
            sender.setPassword(smtpProperties.getPassword());
        }

        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.transport.protocol", "smtp");
        javaMailProperties.put("mail.smtp.auth", String.valueOf(smtpProperties.isAuth()));
        javaMailProperties.put(
                "mail.smtp.starttls.enable", String.valueOf(smtpProperties.isStarttls()));
        javaMailProperties.put(
                "mail.smtp.connectiontimeout",
                String.valueOf(smtpProperties.getConnectionTimeout().toMillis()));
        javaMailProperties.put(
                "mail.smtp.timeout", String.valueOf(smtpProperties.getReadTimeout().toMillis()));
        javaMailProperties.put(
                "mail.smtp.writetimeout",
                String.valueOf(smtpProperties.getWriteTimeout().toMillis()));
        return sender;
    }
}
