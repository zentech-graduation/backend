package com.app.modules.auth.service;

import java.util.UUID;

import com.app.modules.auth.entity.User;

/** Publishes auth-related mail side-effect events through the transactional outbox. */
public interface AuthMailEventService {

    /**
     * Records that a user has completed registration.
     *
     * @param user registered user
     */
    void publishUserRegistered(User user);

    /**
     * Records that an email-verification message should be sent.
     *
     * @param user user who needs email verification
     * @param actorId authenticated actor who requested the email, or {@code null}
     */
    void publishEmailVerificationRequested(User user, UUID actorId);

    /**
     * Records that a password-reset message should be sent.
     *
     * @param user user who requested a password reset
     * @param actorId authenticated actor who requested the reset, or {@code null}
     */
    void publishPasswordResetRequested(User user, UUID actorId);

    /**
     * Records that a password-changed security notification should be sent.
     *
     * @param user user whose password changed
     */
    void publishPasswordChanged(User user);

    /**
     * Records that an OAuth-only account attempted a password reset.
     *
     * @param user OAuth-only user without a local password
     * @param actorId authenticated actor who requested the reset, or {@code null}
     */
    void publishOAuthAccountNoPassword(User user, UUID actorId);
}
