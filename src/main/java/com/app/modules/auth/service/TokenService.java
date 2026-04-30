package com.app.modules.auth.service;

import java.util.UUID;

/**
 * Issuance and consumption of single-use, hashed verification tokens.
 *
 * <p>Two distinct token classes are managed: email-verification tokens (24-hour TTL) and
 * password-reset tokens (15-minute TTL). Tokens are returned to the caller as opaque raw strings
 * but stored only as SHA-256 hashes; raw values must be transmitted to the user out-of-band (email)
 * and never persisted or logged.
 *
 * <p>Issuing a new token of a given class for a user invalidates any previously issued, unused
 * token of the same class for that user.
 */
public interface TokenService {

    /**
     * Issues a new email-verification token for the given user, invalidating any pending one.
     *
     * @param userId user who owns the verification challenge
     * @return raw token to embed in the verification link sent to the user
     */
    String createEmailVerificationToken(UUID userId);

    /**
     * Consumes an email-verification token, marking it used.
     *
     * @param rawToken raw token presented by the user
     * @throws com.app.common.exception.TokenNotFoundException if no token matches the hash
     * @throws com.app.common.exception.TokenExpiredException if the token's expiry has passed
     * @throws com.app.common.exception.TokenAlreadyUsedException if the token has already been
     *     consumed
     */
    void consumeEmailVerificationToken(String rawToken);

    /**
     * Issues a new password-reset token for the given user, invalidating any pending one.
     *
     * @param userId user who owns the reset challenge
     * @return raw token to embed in the password-reset link sent to the user
     */
    String createPasswordResetToken(UUID userId);

    /**
     * Consumes a password-reset token, marking it used.
     *
     * @param rawToken raw token presented by the user
     * @throws com.app.common.exception.TokenNotFoundException if no token matches the hash
     * @throws com.app.common.exception.TokenExpiredException if the token's expiry has passed
     * @throws com.app.common.exception.TokenAlreadyUsedException if the token has already been
     *     consumed
     */
    void consumePasswordResetToken(String rawToken);
}
