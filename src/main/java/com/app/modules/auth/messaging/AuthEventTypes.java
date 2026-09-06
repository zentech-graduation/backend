package com.app.modules.auth.messaging;

import java.util.List;

/** Versioned auth-domain event types published through the transactional outbox. */
public final class AuthEventTypes {

    public static final String USER_REGISTERED_V1 = "user.registered.v1";
    public static final String AUTH_EMAIL_VERIFICATION_REQUESTED_V1 =
            "auth.email-verification.requested.v1";
    public static final String AUTH_PASSWORD_RESET_REQUESTED_V1 =
            "auth.password-reset.requested.v1";
    public static final String AUTH_PASSWORD_CHANGED_V1 = "auth.password-changed.v1";
    public static final String AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1 =
            "auth.oauth-account-no-password.v1";

    public static final List<String> MAIL_EVENT_ROUTING_KEYS =
            List.of(
                    USER_REGISTERED_V1,
                    AUTH_EMAIL_VERIFICATION_REQUESTED_V1,
                    AUTH_PASSWORD_RESET_REQUESTED_V1,
                    AUTH_PASSWORD_CHANGED_V1,
                    AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1);

    private AuthEventTypes() {}
}
