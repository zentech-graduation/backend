package com.app.modules.mail.service;

/**
 * Outbound transactional mail contract.
 *
 * <p>Implementations dispatch mail asynchronously. Provider failures surface as {@code
 * AppException} with {@code SERVICE_UNAVAILABLE}. Raw tokens never appear in log output.
 */
public interface MailService {

    /**
     * Sends an email containing a one-time link the recipient must follow to verify ownership of
     * the address.
     *
     * @param toEmail recipient mailbox
     * @param toName recipient display name used in the greeting
     * @param verificationUrl absolute URL embedding the raw verification token
     */
    void sendEmailVerification(String toEmail, String toName, String verificationUrl);

    /**
     * Sends an email containing a short-lived link the recipient must follow to choose a new
     * password.
     *
     * @param toEmail recipient mailbox
     * @param toName recipient display name used in the greeting
     * @param resetUrl absolute URL embedding the raw password-reset token
     */
    void sendPasswordReset(String toEmail, String toName, String resetUrl);

    /**
     * Sends a welcome email after a new account has been created and verified.
     *
     * @param toEmail recipient mailbox
     * @param toName recipient display name used in the greeting
     */
    void sendWelcome(String toEmail, String toName);

    /**
     * Sends a security notification confirming that the recipient's password has been changed.
     *
     * @param toEmail recipient mailbox
     * @param toName recipient display name used in the greeting
     */
    void sendPasswordChanged(String toEmail, String toName);
}
