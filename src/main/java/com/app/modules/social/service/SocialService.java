package com.app.modules.social.service;

import java.util.List;
import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.social.dto.response.FollowRequestResponse;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.dto.response.SocialUserSummaryResponse;

public interface SocialService {

    FollowResponse followUser(UUID currentUserId, UUID targetUserId);

    void unfollowUser(UUID currentUserId, UUID targetUserId);

    void respondToFollowRequest(UUID currentUserId, UUID requesterId, String action);

    void blockUser(UUID currentUserId, UUID targetUserId);

    void unblockUser(UUID currentUserId, UUID targetUserId);

    CursorPageResponse<SocialUserSummaryResponse> getFollowers(
            UUID targetUserId, UUID currentUserId, String cursor, int limit);

    CursorPageResponse<SocialUserSummaryResponse> getFollowing(
            UUID targetUserId, UUID currentUserId, String cursor, int limit);

    List<FollowRequestResponse> getPendingFollowRequests(UUID currentUserId);

    /**
     * Checks whether an accepted follow relationship exists from the follower to the target user.
     *
     * <p>Pending follow requests do not count as accepted follows; counters and content visibility
     * are gated on accepted status only.
     *
     * @param followerId user performing the follow
     * @param followingId user being followed
     * @return true when a follow row with accepted status exists
     */
    boolean hasAcceptedFollow(UUID followerId, UUID followingId);

    /**
     * Checks whether a block relationship exists between two users in either direction.
     *
     * <p>Blocks are bidirectional in effect: a single row in either direction suppresses all
     * interaction and content visibility between the pair.
     *
     * @param userIdA first user of the pair
     * @param userIdB second user of the pair
     * @return true when either user has blocked the other
     */
    boolean isBlockedBetween(UUID userIdA, UUID userIdB);
}
