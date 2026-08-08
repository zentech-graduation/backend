package com.app.modules.recommendation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.post.dto.response.FeedPostResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for the recommendation module. */
@Tag(name = "Recommendations", description = "Personalized content recommendation endpoints")
@RequestMapping(ApiConstants.Recommendations.ROOT)
public interface RecommendationApi {

    @Operation(
            summary = "Personalized post feed",
            description =
                    "Returns posts ranked for the authenticated user by the recommender. Degrades"
                            + " to the popularity ranking and then to the chronological following"
                            + " feed when the recommender is unavailable. Requires"
                            + " authentication.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "One feed page with opaque continuation cursors"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
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
    @CursorErrorResponses
    @GetMapping(ApiConstants.Recommendations.FEED)
    ResponseEntity<ApiResponse<CursorPageResponse<FeedPostResponse>>> getRecommendedFeed(
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "Page size; maximum 100")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);
}
