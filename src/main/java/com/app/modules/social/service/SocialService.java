package com.app.modules.social.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.common.response.UserListItemResponse;
import com.app.common.response.ViewerRelationshipResponse;
import com.app.modules.social.dto.response.FollowRequestResponse;
import com.app.modules.social.dto.response.FollowResponse;

public interface SocialService {

    FollowResponse followUser(UUID currentUserId, UUID targetUserId);

    void unfollowUser(UUID currentUserId, UUID targetUserId);

    void respondToFollowRequest(UUID currentUserId, UUID requesterId, String action);

    void blockUser(UUID currentUserId, UUID targetUserId);

    void unblockUser(UUID currentUserId, UUID targetUserId);

    CursorPageResponse<UserListItemResponse> getFollowers(
            UUID targetUserId, UUID currentUserId, String cursor, int limit);

    CursorPageResponse<UserListItemResponse> getFollowing(
            UUID targetUserId, UUID currentUserId, String cursor, int limit);

    List<FollowRequestResponse> getPendingFollowRequests(UUID currentUserId);

    /**
     * Returns the IDs of users the viewer currently follows with accepted status, excluding any
     * user involved in a block relationship with the viewer in either direction.
     *
     * <p>Pending follow requests do not count as accepted follows and are excluded. A user is
     * suppressed from the result when either the viewer has blocked them or they have blocked the
     * viewer; the block table stores a single directional row but the practical effect is
     * bidirectional. An empty result means the viewer has no valid feed authors; callers should
     * short-circuit and return an empty page without querying posts.
     *
     * @param viewerId authenticated viewer requesting their feed
     * @return list of user IDs eligible to appear as post authors in the viewer's feed
     */
    List<UUID> getAcceptedFollowingExcludingBlocks(UUID viewerId);

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

    /**
     * Resolves the viewer's follow and block relationship to each of {@code userIds} in two batched
     * queries, one for follows and one for blocks, each covering both directions in a single round
     * trip.
     *
     * @param viewerId the requesting viewer; a null viewer (anonymous) short-circuits to an empty
     *     map without querying
     * @param userIds candidate user ids; may contain duplicates
     * @return a map from each distinct id to its relationship; an id absent from the map (or when
     *     {@code viewerId} is null) has no relationship and must default to {@link
     *     ViewerRelationshipResponse#NONE}
     */
    Map<UUID, ViewerRelationshipResponse> loadRelationships(
            UUID viewerId, Collection<UUID> userIds);
}
