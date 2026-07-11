package com.app.modules.recommendation.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.modules.recommendation.dto.request.ClientEventBatchRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for client-side behavioral event ingestion. */
@Tag(name = "Events", description = "Client behavioral event ingestion for recommendation")
@RequestMapping(ApiConstants.Events.ROOT)
public interface EventsApi {

    @Operation(
            summary = "Ingest a batch of client events",
            description =
                    "Accepts a batch of viewport impressions and post-view events from the"
                            + " authenticated user. The server derives the actor from the JWT and"
                            + " enqueues one outbox row per batch. Returns 202 Accepted.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "202",
                description = "Batch accepted for asynchronous processing",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid batch payload",
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
    @PostMapping
    ResponseEntity<ApiResponse<Void>> ingestEvents(
            @Valid @RequestBody ClientEventBatchRequest request);
}
