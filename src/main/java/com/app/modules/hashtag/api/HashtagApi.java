package com.app.modules.hashtag.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.common.response.PageResponse;
import com.app.modules.hashtag.dto.request.HashtagSearchRequest;
import com.app.modules.hashtag.dto.response.HashtagResponse;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for the hashtag module. */
@Tag(name = "Hashtags", description = "Hashtag search and trending endpoints")
@RequestMapping(ApiConstants.Hashtags.ROOT)
public interface HashtagApi {

    @Operation(
            summary = "Search hashtags",
            description =
                    "Fuzzy hashtag name search using Elasticsearch ngram matching. Falls back to"
                            + " PostgreSQL pg_trgm when Elasticsearch is unavailable.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Search results"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Validation failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Hashtags.SEARCH)
    ResponseEntity<ApiResponse<CursorPageResponse<HashtagResponse>>> search(
            @Valid @ModelAttribute HashtagSearchRequest request);

    @Operation(
            summary = "List trending hashtags",
            description = "Returns the latest trending hashtag snapshot ordered by rank ascending.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Trending hashtag list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(ApiConstants.Hashtags.TRENDING)
    ResponseEntity<ApiResponse<PageResponse<HashtagTrendingResponse>>> trending(
            @Parameter(description = "Zero-based page index")
                    @RequestParam(defaultValue = "0")
                    @Min(0)
                    int page,
            @Parameter(description = "Page size; maximum 100")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int size);
}
