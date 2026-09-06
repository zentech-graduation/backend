package com.app.modules.mail.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.util.MailTemplateRenderer;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;

/**
 * Synchronous Resend-backed mail sender.
 *
 * <p>RabbitMQ consumers use this component so provider failures are observed before acknowledging a
 * message. Raw tokens must only appear inside the final recipient URL and must never be logged.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "resend")
public class ResendMailSender extends AbstractTemplateMailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendMailSender.class);

    private final Resend resend;

    public ResendMailSender(
            Resend resend,
            MailProperties mailProperties,
            MailTemplateRenderer mailTemplateRenderer) {
        super(mailProperties, mailTemplateRenderer);
        this.resend = resend;
    }

    @Override
    protected void deliver(String toEmail, String subject, String htmlBody) {
        CreateEmailOptions options =
                CreateEmailOptions.builder()
                        .from(fromHeader())
                        .to(toEmail)
                        .subject(subject)
                        .html(htmlBody)
                        .build();
        try {
            resend.emails().send(options);
            log.info("Email sent | subject: {}", subject);
        } catch (Exception e) {
            log.error("Failed to send email | subject: {} | error: {}", subject, e.getMessage());
            throw new AppException(ApiErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
