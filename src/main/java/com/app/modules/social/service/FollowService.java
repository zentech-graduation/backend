package com.app.modules.social.service;

import java.util.UUID;

import com.app.modules.social.dto.response.FollowResponse;

/** Domain API for social graph follow operations. */
public interface FollowService {

    /**
     * Creates a follow relationship from the actor to the target user.
     *
     * <p>Public targets are accepted immediately; private targets create a pending follow request.
     * The operation writes the canonical follow row and records the matching outbox event in the
     * same transaction.
     *
     * @param followerId authenticated user performing the follow
     * @param targetUserId user being followed
     * @return persisted follow relationship
     */
    FollowResponse follow(UUID followerId, UUID targetUserId);
}
