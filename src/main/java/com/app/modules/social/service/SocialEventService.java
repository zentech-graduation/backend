package com.app.modules.social.service;

import java.util.UUID;

import com.app.modules.social.entity.Follow;

/** Publishes social-domain side-effect events through the transactional outbox. */
public interface SocialEventService {

    /**
     * Records the business event produced by a newly-created follow row.
     *
     * @param follow persisted follow relationship
     */
    void publishFollowCreated(Follow follow);

    /**
     * Records a recommendation {@code profile_follow} interaction when a follow becomes accepted
     * (either immediately for a public target or on follow-request approval).
     *
     * @param followerId the user who follows
     * @param followingId the user being followed
     */
    void publishProfileFollowInteraction(UUID followerId, UUID followingId);

    /**
     * Records a recommendation {@code profile_unfollow} interaction when a follow is removed.
     *
     * @param followerId the user who unfollowed
     * @param followingId the user formerly followed
     */
    void publishProfileUnfollowInteraction(UUID followerId, UUID followingId);
}
