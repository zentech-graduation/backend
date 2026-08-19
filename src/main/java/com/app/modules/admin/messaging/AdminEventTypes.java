package com.app.modules.admin.messaging;

/** Versioned admin domain event types published through the transactional outbox. */
public final class AdminEventTypes {

    /**
     * Emitted when a warning is issued, so the warned account is told.
     *
     * <p>Named for what happened to the account rather than for what a moderator did, because the
     * consumer's only job is to notify the account, and the moderator is deliberately not named in
     * what it delivers.
     */
    public static final String USER_WARNED_V1 = "user.warned.v1";

    private AdminEventTypes() {}
}
