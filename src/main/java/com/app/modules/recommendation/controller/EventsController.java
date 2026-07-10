package com.app.modules.recommendation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.recommendation.api.EventsApi;
import com.app.modules.recommendation.dto.request.ClientEventBatchRequest;
import com.app.modules.recommendation.service.RecommendationEventIngestionService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;

/** HTTP surface for client-side behavioral event ingestion. */
@RestController
public class EventsController extends BaseController implements EventsApi {

    private final RecommendationEventIngestionService ingestionService;

    public EventsController(RecommendationEventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** Accepts a client event batch from the authenticated user; returns 202. */
    @Override
    @PostMapping
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<Void>> ingestEvents(
            @Valid @RequestBody ClientEventBatchRequest request) {
        ingestionService.ingestBatch(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(ApiSuccessCode.ACCEPTED));
    }
}
