package com.app.modules.recommendation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.recommendation.api.RecommendationApi;
import com.app.modules.recommendation.service.RecommendationFeedService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for personalized recommendations. */
@RestController
public class RecommendationController extends BaseController implements RecommendationApi {

    private final RecommendationFeedService recommendationFeedService;

    public RecommendationController(RecommendationFeedService recommendationFeedService) {
        this.recommendationFeedService = recommendationFeedService;
    }

    /**
     * Serves one page of the personalized feed for the authenticated user.
     *
     * <p>Accepts an optional opaque cursor and page size, returns ranked published posts visible to
     * the caller. Requires authentication.
     */
    @Override
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<FeedPostResponse>>> getRecommendedFeed(
            String cursor, int limit) {
        CursorPageResponse<FeedPostResponse> page =
                recommendationFeedService.getRecommendedFeed(
                        SecurityUtils.getCurrentUserId(), cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, page));
    }
}
