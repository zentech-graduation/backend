package com.app.modules.mail.service;

/** Synchronous transactional mail sender used by RabbitMQ consumers. */
public interface MailSender {

    void sendEmailVerification(String toEmail, String toName, String verificationUrl);

    void sendPasswordReset(String toEmail, String toName, String resetUrl);

    void sendWelcome(String toEmail, String toName);

    void sendPasswordChanged(String toEmail, String toName);

    void sendOAuthAccountNoPassword(String toEmail, String displayName);
}
