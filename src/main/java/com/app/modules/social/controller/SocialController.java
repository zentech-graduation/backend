package com.app.modules.social.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.social.dto.response.FollowRequestResponse;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.dto.response.SocialUserSummaryResponse;
import com.app.modules.social.service.SocialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(ApiConstants.Social.ROOT)
@RequiredArgsConstructor
@Tag(
        name = "Social",
        description =
                "User relationships, follow requests, blocking, followers, and following flows")
public class SocialController extends BaseController {

    private final SocialService socialService;

    @PostMapping("/follow/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Follow a user or send follow request if account is private")
    public FollowResponse follow(@PathVariable UUID targetUserId) {
        return socialService.followUser(SecurityUtils.getCurrentUserId(), targetUserId);
    }

    @DeleteMapping("/follow/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unfollow a user or cancel pending follow request")
    public void unfollow(@PathVariable UUID targetUserId) {
        socialService.unfollowUser(SecurityUtils.getCurrentUserId(), targetUserId);
    }

    @GetMapping("/follow-requests")
    @Operation(summary = "Get pending follow requests for current user")
    public List<FollowRequestResponse> getPendingFollowRequests() {
        return socialService.getPendingFollowRequests(SecurityUtils.getCurrentUserId());
    }

    @PatchMapping("/follow-requests/{requesterId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Approve a pending follow request")
    public void approveFollowRequest(@PathVariable UUID requesterId) {
        socialService.respondToFollowRequest(
                SecurityUtils.getCurrentUserId(), requesterId, "approve");
    }

    @PatchMapping("/follow-requests/{requesterId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a pending follow request")
    public void rejectFollowRequest(@PathVariable UUID requesterId) {
        socialService.respondToFollowRequest(
                SecurityUtils.getCurrentUserId(), requesterId, "reject");
    }

    @PostMapping("/block/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Block user and clean up follow relationship both ways")
    public void block(@PathVariable UUID targetUserId) {
        socialService.blockUser(SecurityUtils.getCurrentUserId(), targetUserId);
    }

    @DeleteMapping("/block/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unblock user")
    public void unblock(@PathVariable UUID targetUserId) {
        socialService.unblockUser(SecurityUtils.getCurrentUserId(), targetUserId);
    }

    @GetMapping("/users/{userId}/followers")
    @Operation(summary = "Get followers with cursor pagination")
    public CursorPageResponse<SocialUserSummaryResponse> getFollowers(
            @PathVariable UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return socialService.getFollowers(userId, SecurityUtils.getCurrentUserId(), cursor, limit);
    }

    @GetMapping("/users/{userId}/following")
    @Operation(summary = "Get following list with cursor pagination")
    public CursorPageResponse<SocialUserSummaryResponse> getFollowing(
            @PathVariable UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return socialService.getFollowing(userId, SecurityUtils.getCurrentUserId(), cursor, limit);
    }
}
