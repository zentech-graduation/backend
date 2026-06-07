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
}
