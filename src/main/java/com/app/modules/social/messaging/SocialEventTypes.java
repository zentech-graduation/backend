package com.app.modules.social.messaging;

/** Versioned social-domain event types published through the transactional outbox. */
public final class SocialEventTypes {

    public static final String USER_FOLLOWED_V1 = "user.followed.v1";
    public static final String USER_FOLLOW_REQUESTED_V1 = "user.follow-requested.v1";

    private SocialEventTypes() {}
}
