package com.app.modules.auth.service;

/**
 * Records email-verification resend events in a durable transaction while preserving
 * account-enumeration behaviour.
 */
public interface AuthResendVerificationEventService {

    /**
     * Records the email-verification event for a resend request when the account exists and is not
     * yet verified. Unknown or already-verified accounts are ignored silently.
     *
     * @param email candidate account email
     */
    void recordResendVerificationRequest(String email);
}
