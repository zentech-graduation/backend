package com.app.modules.auth.service;

/**
 * Records forgot-password mail events in a durable transaction while preserving account-enumeration
 * behaviour.
 */
public interface AuthForgotPasswordEventService {

    /**
     * Records the appropriate mail event for a forgot-password request when the account exists and
     * is eligible. Unknown or inactive accounts are ignored silently.
     *
     * @param email candidate account email
     */
    void recordForgotPasswordRequest(String email);
}
