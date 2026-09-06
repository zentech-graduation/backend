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
import com.app.common.security.util.SecurityUtils;
import com.app.modules.post.api.PostSaveApi;
import com.app.modules.post.dto.response.SavedPostResponse;
import com.app.modules.post.service.PostSaveService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** HTTP surface for post save actions and the saved-posts list. */
@RestController
public class PostSaveController extends BaseController implements PostSaveApi {

    private final PostSaveService postSaveService;

    public PostSaveController(PostSaveService postSaveService) {
        this.postSaveService = postSaveService;
    }

    /** Saves a post for the authenticated user; returns 201. */
    @Override
    @PostMapping(ApiConstants.Posts.SAVE)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> savePost(@PathVariable("postId") UUID postId) {
        postSaveService.savePost(SecurityUtils.getCurrentUserId(), postId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED));
    }

    /** Removes the authenticated user's bookmark; returns 204. */
    @Override
    @DeleteMapping(ApiConstants.Posts.SAVE)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<Void> unsavePost(@PathVariable("postId") UUID postId) {
        postSaveService.unsavePost(SecurityUtils.getCurrentUserId(), postId);
        return ResponseEntity.noContent().build();
    }

    /** Lists the authenticated user's saved posts, newest save first. */
    @Override
    @GetMapping(ApiConstants.Posts.SAVED)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<SavedPostResponse>>> listSavedPosts(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        CursorPageResponse<SavedPostResponse> body =
                postSaveService.listSavedPosts(SecurityUtils.getCurrentUserId(), cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, body));
    }
}
