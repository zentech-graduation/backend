package com.app.modules.mail.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.app.common.config.MailProperties;
import com.app.modules.mail.MailSendException;
import com.app.modules.mail.MailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

/**
 * Resend-backed {@link MailService}.
 *
 * <p>All public methods are dispatched on the {@code mailTaskExecutor} pool to keep the request
 * path non-blocking. Provider errors are wrapped as {@link MailSendException}; raw tokens never
 * appear in log output.
 */
@Service
public class MailServiceImpl implements MailService {

    public static final int EMAIL_VERIFICATION_EXPIRY_HOURS = 24;
    public static final int PASSWORD_RESET_EXPIRY_MINUTES = 15;

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private static final String SUBJECT_EMAIL_VERIFICATION = "Verify your email address";
    private static final String SUBJECT_PASSWORD_RESET = "Reset your password";
    private static final String SUBJECT_WELCOME = "Welcome to VietRecruit";
    private static final String SUBJECT_PASSWORD_CHANGED = "Your password has been changed";

    private static final String TEMPLATE_EMAIL_VERIFICATION = "mail/email-verification";
    private static final String TEMPLATE_PASSWORD_RESET = "mail/password-reset";
    private static final String TEMPLATE_WELCOME = "mail/welcome";
    private static final String TEMPLATE_PASSWORD_CHANGED = "mail/password-changed";

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ENGLISH);

    private final Resend resend;
    private final MailProperties mailProperties;
    private final TemplateEngine templateEngine;

    public MailServiceImpl(
            Resend resend, MailProperties mailProperties, TemplateEngine templateEngine) {
        this.resend = resend;
        this.mailProperties = mailProperties;
        this.templateEngine = templateEngine;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendEmailVerification(String toEmail, String toName, String verificationUrl) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("recipientName", toName);
        ctx.setVariable("verificationUrl", verificationUrl);
        ctx.setVariable("expiryHours", EMAIL_VERIFICATION_EXPIRY_HOURS);
        String html = templateEngine.process(TEMPLATE_EMAIL_VERIFICATION, ctx);
        dispatch(toEmail, SUBJECT_EMAIL_VERIFICATION, html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordReset(String toEmail, String toName, String resetUrl) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("recipientName", toName);
        ctx.setVariable("resetUrl", resetUrl);
        ctx.setVariable("expiryMinutes", PASSWORD_RESET_EXPIRY_MINUTES);
        String html = templateEngine.process(TEMPLATE_PASSWORD_RESET, ctx);
        dispatch(toEmail, SUBJECT_PASSWORD_RESET, html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendWelcome(String toEmail, String toName) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("recipientName", toName);
        String html = templateEngine.process(TEMPLATE_WELCOME, ctx);
        dispatch(toEmail, SUBJECT_WELCOME, html);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordChanged(String toEmail, String toName) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("recipientName", toName);
        ctx.setVariable(
                "changeTimestamp", OffsetDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FORMAT));
        String html = templateEngine.process(TEMPLATE_PASSWORD_CHANGED, ctx);
        dispatch(toEmail, SUBJECT_PASSWORD_CHANGED, html);
    }

    private void dispatch(String toEmail, String subject, String html) {
        CreateEmailOptions params =
                CreateEmailOptions.builder()
                        .from(formatFromHeader())
                        .to(toEmail)
                        .subject(subject)
                        .html(html)
                        .build();
        try {
            resend.emails().send(params);
            log.info("mail.sent subject=\"{}\" to=\"{}\"", subject, toEmail);
        } catch (ResendException e) {
            log.error("mail.failed subject=\"{}\" to=\"{}\": {}", subject, toEmail, e.getMessage());
            throw new MailSendException("Failed to send email via Resend", e);
        }
    }

    private String formatFromHeader() {
        return mailProperties.fromName() + " <" + mailProperties.fromAddress() + ">";
    }
}
