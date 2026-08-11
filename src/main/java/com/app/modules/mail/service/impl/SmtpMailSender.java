package com.app.modules.mail.service.impl;

import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.util.MailTemplateRenderer;

/**
 * Synchronous SMTP-backed mail sender.
 *
 * <p>Targets a local sink such as Mailpit so development and tests never reach the production mail
 * provider. Delivery failures collapse to the same error code the provider transport raises, so
 * consumers observe one failure shape regardless of transport.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "smtp")
public class SmtpMailSender extends AbstractTemplateMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;

    public SmtpMailSender(
            JavaMailSender javaMailSender,
            MailProperties mailProperties,
            MailTemplateRenderer mailTemplateRenderer) {
        super(mailProperties, mailTemplateRenderer);
        this.javaMailSender = javaMailSender;
    }

    @Override
    protected void deliver(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromHeader());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            javaMailSender.send(message);
            log.info("Email sent | subject: {}", subject);
        } catch (Exception e) {
            log.error("Failed to send email | subject: {} | error: {}", subject, e.getMessage());
            throw new AppException(ApiErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
