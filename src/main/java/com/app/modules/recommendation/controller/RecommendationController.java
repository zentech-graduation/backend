package com.app.modules.recommendation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.recommendation.api.RecommendationApi;
import com.app.modules.recommendation.dto.request.ImpressionBatchRequest;
import com.app.modules.recommendation.service.ImpressionService;
import com.app.modules.recommendation.service.RecommendationFeedService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for personalized recommendations. */
@RestController
public class RecommendationController extends BaseController implements RecommendationApi {

    private final RecommendationFeedService recommendationFeedService;
    private final ImpressionService impressionService;

    public RecommendationController(
            RecommendationFeedService recommendationFeedService,
            ImpressionService impressionService) {
        this.recommendationFeedService = recommendationFeedService;
        this.impressionService = impressionService;
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

    /**
     * Accepts a batch of post impressions from the authenticated viewer.
     *
     * <p>Writes one outbox row per impression inside the request transaction and returns; the
     * recommender is reached asynchronously, so this never blocks on it. Deliberately distinct from
     * {@code POST /posts/{postId}/view} and must not be merged with it: that endpoint records one
     * deliberate open of a single post, this one ingests batched passive viewport impressions
     * carrying dwell. Neither writes {@code posts.view_count}, which is maintained by a background
     * job and never written from application code. Requires authentication.
     */
    @Override
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> recordImpressions(ImpressionBatchRequest request) {
        impressionService.recordBatch(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(ApiSuccessCode.OK, null));
    }
}
