package com.app.modules.post.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.response.UserSummaryResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.post.api.PostLikeApi;
import com.app.modules.post.dto.response.LikeActionResponse;
import com.app.modules.post.service.PostLikeService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** HTTP surface for post like actions and liker listings. */
@RestController
public class PostLikeController extends BaseController implements PostLikeApi {

    private final PostLikeService postLikeService;

    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    /** Likes a post for the authenticated user; returns 201 with the fresh like count. */
    @Override
    @PostMapping(ApiConstants.Posts.LIKE)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<LikeActionResponse>> likePost(
            @PathVariable("postId") UUID postId) {
        LikeActionResponse body =
                postLikeService.likePost(SecurityUtils.getCurrentUserId(), postId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED, body));
    }

    /** Removes the authenticated user's like; returns the fresh like count. */
    @Override
    @DeleteMapping(ApiConstants.Posts.LIKE)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<LikeActionResponse>> unlikePost(
            @PathVariable("postId") UUID postId) {
        LikeActionResponse body =
                postLikeService.unlikePost(SecurityUtils.getCurrentUserId(), postId);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }

    /** Lists users who liked the post, newest like first. */
    @Override
    @GetMapping(ApiConstants.Posts.LIKES)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserSummaryResponse>>> listLikers(
            @PathVariable("postId") UUID postId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        CursorPageResponse<UserSummaryResponse> body =
                postLikeService.listLikers(SecurityUtils.getCurrentUserId(), postId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }
}
