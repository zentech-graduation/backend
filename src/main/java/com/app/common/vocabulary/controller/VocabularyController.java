package com.app.common.vocabulary.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.vocabulary.api.VocabularyApi;
import com.app.common.vocabulary.dto.response.VocabularyResponse;
import com.app.common.vocabulary.service.VocabularyService;
import com.app.common.web.StrictQueryParameters;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/**
 * REST endpoint for the read-only configuration surface.
 *
 * <p>No role gate beyond authentication. The rows are display metadata for values the API already
 * publishes as enum sets in its own document, so nothing here is privileged.
 */
@RestController
public class VocabularyController extends BaseController implements VocabularyApi {

    private final VocabularyService vocabularyService;

    public VocabularyController(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    /** Returns every display vocabulary in one response. */
    @Override
    @GetMapping(ApiConstants.Config.VOCABULARIES)
    @StrictQueryParameters
    @RateLimiter(name = "highTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<VocabularyResponse>> getVocabularies() {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, vocabularyService.getVocabularies()));
    }
}
