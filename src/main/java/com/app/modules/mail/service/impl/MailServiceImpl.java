package com.app.modules.mail.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.enums.MailTemplate;
import com.app.modules.mail.service.MailService;
import com.app.modules.mail.util.MailTemplateRenderer;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;

/**
 * Resend-backed {@link MailService}.
 *
 * <p>All public methods run on the {@code mailTaskExecutor} pool to keep the request path
 * non-blocking. Provider errors are logged and re-thrown as {@link AppException} with {@code
 * SERVICE_UNAVAILABLE}; raw tokens never appear in log output.
 */
@Service
public class MailServiceImpl implements MailService {

    private static final int EMAIL_VERIFICATION_EXPIRY_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRY_MINUTES = 15;

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final Resend resend;
    private final MailProperties mailProperties;
    private final MailTemplateRenderer mailTemplateRenderer;

    public MailServiceImpl(
            Resend resend,
            MailProperties mailProperties,
            MailTemplateRenderer mailTemplateRenderer) {
        this.resend = resend;
        this.mailProperties = mailProperties;
        this.mailTemplateRenderer = mailTemplateRenderer;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendEmailVerification(String toEmail, String toName, String verificationUrl) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        variables.put("verificationUrl", verificationUrl);
        variables.put("expiryHours", EMAIL_VERIFICATION_EXPIRY_HOURS);
        String html = mailTemplateRenderer.render(MailTemplate.EMAIL_VERIFICATION, variables);
        send(toEmail, MailTemplate.EMAIL_VERIFICATION.getDefaultSubject(), html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordReset(String toEmail, String toName, String resetUrl) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        variables.put("resetUrl", resetUrl);
        variables.put("expiryMinutes", PASSWORD_RESET_EXPIRY_MINUTES);
        String html = mailTemplateRenderer.render(MailTemplate.PASSWORD_RESET, variables);
        send(toEmail, MailTemplate.PASSWORD_RESET.getDefaultSubject(), html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendWelcome(String toEmail, String toName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        String html = mailTemplateRenderer.render(MailTemplate.WELCOME, variables);
        send(toEmail, MailTemplate.WELCOME.getDefaultSubject(), html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordChanged(String toEmail, String toName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        String html = mailTemplateRenderer.render(MailTemplate.PASSWORD_CHANGED, variables);
        send(toEmail, MailTemplate.PASSWORD_CHANGED.getDefaultSubject(), html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendOAuthAccountNoPassword(String toEmail, String displayName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", displayName);
        variables.put("appName", mailProperties.getAppName());
        variables.put("frontendBaseUrl", mailProperties.getFrontendBaseUrl());
        String html =
                mailTemplateRenderer.render(MailTemplate.OAUTH_ACCOUNT_NO_PASSWORD, variables);
        send(toEmail, MailTemplate.OAUTH_ACCOUNT_NO_PASSWORD.getDefaultSubject(), html);
    }

    private void send(String toEmail, String subject, String htmlBody) {
        String from = mailProperties.getFromName() + " <" + mailProperties.getFromAddress() + ">";
        CreateEmailOptions options =
                CreateEmailOptions.builder()
                        .from(from)
                        .to(toEmail)
                        .subject(subject)
                        .html(htmlBody)
                        .build();
        try {
            resend.emails().send(options);
            log.info("Email sent to {} | subject: {}", toEmail, subject);
        } catch (Exception e) {
            log.error(
                    "Failed to send email to {} | subject: {} | error: {}",
                    toEmail,
                    subject,
                    e.getMessage());
            throw new AppException(ApiErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
