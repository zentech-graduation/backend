package com.app.modules.mail.service;

import java.util.UUID;

import com.app.common.exception.AppException;

/** Campaign mail opt-out, usable without a session. */
public interface MailUnsubscribeService {

    /**
     * Returns the recipient's unsubscribe link token, minting one on first use.
     *
     * @param userId the recipient
     * @return the raw token to embed in the link; only its hash is stored
     */
    String tokenFor(UUID userId);

    /**
     * Opts an account out of campaign mail.
     *
     * <p>Deliberately idempotent: a mail client that prefetches the link, or a recipient who clicks
     * twice, must not see an error for an action that already succeeded.
     *
     * @param rawToken the token from the link
     * @throws AppException {@code UNSUBSCRIBE_TOKEN_INVALID} when no account holds that token
     */
    void unsubscribe(String rawToken);
}
