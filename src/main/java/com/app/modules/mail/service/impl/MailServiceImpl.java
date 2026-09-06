package com.app.modules.mail.service.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.app.modules.mail.service.MailSender;
import com.app.modules.mail.service.MailService;

/**
 * Async facade for legacy request-path mail dispatch.
 *
 * <p>RabbitMQ consumers must use {@link MailSender} directly so provider failures are observed
 * before message acknowledgement.
 */
@Service
public class MailServiceImpl implements MailService {

    private final MailSender mailSender;

    public MailServiceImpl(MailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendEmailVerification(String toEmail, String toName, String verificationUrl) {
        mailSender.sendEmailVerification(toEmail, toName, verificationUrl);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordReset(String toEmail, String toName, String resetUrl) {
        mailSender.sendPasswordReset(toEmail, toName, resetUrl);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendWelcome(String toEmail, String toName) {
        mailSender.sendWelcome(toEmail, toName);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordChanged(String toEmail, String toName) {
        mailSender.sendPasswordChanged(toEmail, toName);
    }

    @Override
    @Async("mailTaskExecutor")
    public void sendOAuthAccountNoPassword(String toEmail, String displayName) {
        mailSender.sendOAuthAccountNoPassword(toEmail, displayName);
    }
}
