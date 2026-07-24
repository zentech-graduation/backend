package com.app.modules.hashtag.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.response.PageResponse;
import com.app.modules.hashtag.api.HashtagApi;
import com.app.modules.hashtag.dto.request.HashtagSearchRequest;
import com.app.modules.hashtag.dto.response.HashtagResponse;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;
import com.app.modules.hashtag.service.HashtagSearchService;
import com.app.modules.hashtag.service.HashtagTrendingService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** HTTP surface for hashtag search and trending endpoints. */
@RestController
public class HashtagController extends BaseController implements HashtagApi {

    private static final int DEFAULT_SEARCH_LIMIT = 20;

    private final HashtagSearchService hashtagSearchService;
    private final HashtagTrendingService hashtagTrendingService;

    public HashtagController(
            HashtagSearchService hashtagSearchService,
            HashtagTrendingService hashtagTrendingService) {
        this.hashtagSearchService = hashtagSearchService;
        this.hashtagTrendingService = hashtagTrendingService;
    }

    /** Fuzzy hashtag name search; falls back to pg_trgm when Elasticsearch is unavailable. */
    @Override
    @GetMapping(ApiConstants.Hashtags.SEARCH)
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<CursorPageResponse<HashtagResponse>>> search(
            @Valid @ModelAttribute HashtagSearchRequest request) {
        int limit = request.limit() != null ? request.limit() : DEFAULT_SEARCH_LIMIT;
        CursorPageResponse<HashtagResponse> result =
                hashtagSearchService.search(request.q(), request.cursor(), limit);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, result));
    }

    /** Returns the latest trending hashtag snapshot. */
    @Override
    @GetMapping(ApiConstants.Hashtags.TRENDING)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<PageResponse<HashtagTrendingResponse>>> trending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Ordering is always rank ASC within the latest period (HashtagTrendingServiceImpl);
        // an unsorted Pageable keeps a client-supplied sort field from reaching the repository
        // query and failing with a PropertyReferenceException for fields HashtagTrending lacks.
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<HashtagTrendingResponse> result = hashtagTrendingService.getTrending(pageable);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.OK, result));
    }
}
