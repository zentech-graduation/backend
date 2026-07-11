package com.app.modules.recommendation.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/events}, a client-flushed batch of viewport impressions and
 * post-view events.
 *
 * <p>The server derives {@code userId} from the authenticated principal, never from the payload, so
 * a client cannot write events for another user. Validation caps the batch size and rejects items
 * with negative positions or unknown sources before any outbox enqueue.
 *
 * @param sessionId client session id
 * @param requestId page-level id shared by every item served in one explore response
 * @param items impression/post-view rows; the size cap comes from {@code
 *     app.recommendation.events.max-batch-size} (default 50), enforced in the ingestion service so
 *     the knob stays authoritative
 */
public record ClientEventBatchRequest(
        @NotNull UUID sessionId, @NotNull UUID requestId, @NotEmpty @Valid List<Item> items) {

    /**
     * One client-side event.
     *
     * @param clientEventId client-generated id used to derive a deterministic row id
     * @param type {@code impression} or {@code post_view}
     * @param postId the post that was shown
     * @param position zero-based position within the served page
     * @param source candidate source: {@code cf}, {@code content}, {@code graph}, {@code trending},
     *     {@code explore_slot}, {@code fallback}
     */
    public record Item(
            @NotNull UUID clientEventId,
            @NotBlank String type,
            @NotNull UUID postId,
            @Min(0) int position,
            @NotBlank String source) {}
}
