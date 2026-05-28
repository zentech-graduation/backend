package com.app.modules.social.service;

import com.app.modules.social.entity.Follow;

/** Publishes social-domain side-effect events through the transactional outbox. */
public interface SocialEventService {

    /**
     * Records the business event produced by a newly-created follow row.
     *
     * @param follow persisted follow relationship
     */
    void publishFollowCreated(Follow follow);
}
