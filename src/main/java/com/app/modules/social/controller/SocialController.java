package com.app.modules.social.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.social.api.SocialApi;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.service.FollowService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for social graph operations. */
@RestController
public class SocialController extends BaseController implements SocialApi {

    private final FollowService followService;

    public SocialController(FollowService followService) {
        this.followService = followService;
    }

    /** Creates a follow relationship for the authenticated user and returns its stored state. */
    @Override
    @PostMapping(ApiConstants.Social.FOLLOW)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<FollowResponse>> follow(@PathVariable UUID targetUserId) {
        UUID followerId = SecurityUtils.getCurrentUserId();
        FollowResponse response = followService.follow(followerId, targetUserId);
        return ResponseEntity.status(ApiSuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.success(ApiSuccessCode.CREATED, response));
    }
}
