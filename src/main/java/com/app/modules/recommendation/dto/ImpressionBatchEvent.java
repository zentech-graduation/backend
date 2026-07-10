package com.app.modules.recommendation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code rec.impression.batch.v1}, a client-flushed batch of viewport impressions.
 *
 * @param sessionId client session id
 * @param platform {@code ios}, {@code android}, or {@code web}
 * @param requestId page-level id shared by every item served in one explore response; groups one
 *     served page for CTR attribution
 * @param items impression rows; the consumer derives a deterministic row id per item so a replayed
 *     batch after partial failure never duplicates rows
 */
public record ImpressionBatchEvent(
        UUID sessionId, String platform, UUID requestId, List<Item> items) {

    /**
     * One client-side impression or post-view event.
     *
     * @param clientEventId client-generated id; the consumer hashes it into a deterministic row id
     * @param type {@code impression} or {@code post_view}
     * @param postId the post that was shown
     * @param position zero-based position within the served page, for position-bias correction
     * @param source candidate source ({@code cf}, {@code content}, {@code graph}, {@code trending},
     *     {@code explore_slot}, {@code fallback})
     * @param occurredAt client-reported timestamp; the consumer trusts it for event ordering
     */
    public record Item(
            UUID clientEventId,
            String type,
            UUID postId,
            int position,
            String source,
            OffsetDateTime occurredAt) {}
}
