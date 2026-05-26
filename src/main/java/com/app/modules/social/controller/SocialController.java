package com.app.modules.social.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.social.api.SocialApi;
import com.app.modules.social.dto.response.FollowRequestResponse;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.dto.response.SocialUserSummaryResponse;
import com.app.modules.social.service.SocialService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SocialController extends BaseController implements SocialApi {

    private final SocialService socialService;

    @Override
    public ResponseEntity<ApiResponse<FollowResponse>> follow(UUID targetUserId) {
        FollowResponse body =
                socialService.followUser(SecurityUtils.getCurrentUserId(), targetUserId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED, body));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> unfollow(UUID targetUserId) {
        socialService.unfollowUser(SecurityUtils.getCurrentUserId(), targetUserId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(ApiSuccessCode.NO_CONTENT));
    }

    @Override
    public ResponseEntity<ApiResponse<List<FollowRequestResponse>>> getPendingFollowRequests() {
        List<FollowRequestResponse> body =
                socialService.getPendingFollowRequests(SecurityUtils.getCurrentUserId());

        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> approveFollowRequest(UUID requesterId) {
        socialService.respondToFollowRequest(
                SecurityUtils.getCurrentUserId(), requesterId, "approve");

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(ApiSuccessCode.NO_CONTENT));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> rejectFollowRequest(UUID requesterId) {
        socialService.respondToFollowRequest(
                SecurityUtils.getCurrentUserId(), requesterId, "reject");

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(ApiSuccessCode.NO_CONTENT));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> block(UUID targetUserId) {
        socialService.blockUser(SecurityUtils.getCurrentUserId(), targetUserId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> unblock(UUID targetUserId) {
        socialService.unblockUser(SecurityUtils.getCurrentUserId(), targetUserId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success(ApiSuccessCode.NO_CONTENT));
    }

    @Override
    public ResponseEntity<ApiResponse<CursorPageResponse<SocialUserSummaryResponse>>> getFollowers(
            UUID userId, String cursor, int limit) {
        CursorPageResponse<SocialUserSummaryResponse> body =
                socialService.getFollowers(userId, SecurityUtils.getCurrentUserId(), cursor, limit);

        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    @Override
    public ResponseEntity<ApiResponse<CursorPageResponse<SocialUserSummaryResponse>>> getFollowing(
            UUID userId, String cursor, int limit) {
        CursorPageResponse<SocialUserSummaryResponse> body =
                socialService.getFollowing(userId, SecurityUtils.getCurrentUserId(), cursor, limit);

        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }
}
