package com.app.modules.post.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload for a post index upsert event consumed by the search-index sync worker.
 *
 * <p>Carries the canonical post state required to materialize or refresh the corresponding
 * Elasticsearch document.
 *
 * @param eventId unique identifier of this event instance
 * @param version schema version of the event payload
 * @param occurredAt instant the source state change occurred
 * @param postId identifier of the post to index
 * @param userId identifier of the post author
 * @param caption post caption; may be {@code null} when the post has no caption
 * @param status post status as the lowercase string value (e.g. {@code "published"})
 * @param hashtagIds identifiers of hashtags associated with the post
 * @param createdAt instant the post row was created
 */
public record PostIndexUpsertEvent(
        UUID eventId,
        Integer version,
        OffsetDateTime occurredAt,
        UUID postId,
        UUID userId,
        String caption,
        String status,
        List<String> hashtagIds,
        OffsetDateTime createdAt) {}
