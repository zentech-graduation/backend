package com.app.modules.mail.service;

import com.app.common.exception.AppException;

/** Synchronous transactional mail sender used by RabbitMQ consumers. */
public interface MailSender {

    /**
     * Sends the email verification message containing a link to confirm the recipient's address.
     *
     * @param toEmail recipient email address
     * @param toName recipient display name, interpolated into the template
     * @param verificationUrl full URL the recipient follows to verify their email; carries the raw
     *     verification token and must never be logged
     * @throws AppException when the mail provider rejects or fails to send the message
     */
    void sendEmailVerification(String toEmail, String toName, String verificationUrl);

    /**
     * Sends the password reset message containing a link to set a new password.
     *
     * @param toEmail recipient email address
     * @param toName recipient display name, interpolated into the template
     * @param resetUrl full URL the recipient follows to reset their password; carries the raw reset
     *     token and must never be logged
     * @throws AppException when the mail provider rejects or fails to send the message
     */
    void sendPasswordReset(String toEmail, String toName, String resetUrl);

    /**
     * Sends the welcome message after a new account is created.
     *
     * @param toEmail recipient email address
     * @param toName recipient display name, interpolated into the template
     * @throws AppException when the mail provider rejects or fails to send the message
     */
    void sendWelcome(String toEmail, String toName);

    /**
     * Sends the password-changed notification confirming a completed password change.
     *
     * @param toEmail recipient email address
     * @param toName recipient display name, interpolated into the template
     * @throws AppException when the mail provider rejects or fails to send the message
     */
    void sendPasswordChanged(String toEmail, String toName);

    /**
     * Sends the notice informing an OAuth-only account holder that their account has no password
     * set and cannot use email/password login.
     *
     * @param toEmail recipient email address
     * @param displayName recipient display name, interpolated into the template
     * @throws AppException when the mail provider rejects or fails to send the message
     */
    void sendOAuthAccountNoPassword(String toEmail, String displayName);
}
