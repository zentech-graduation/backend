package com.app.modules.hashtag.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload for a hashtag index upsert event consumed by the search-index sync worker.
 *
 * <p>Carries the canonical hashtag state required to materialize or refresh the corresponding
 * Elasticsearch document.
 *
 * @param eventId unique identifier of this event instance
 * @param version schema version of the event payload
 * @param occurredAt instant the source state change occurred
 * @param hashtagId identifier of the hashtag to index
 * @param name hashtag name as stored, without the leading {@code #}
 * @param postCount denormalized count of posts associated with the hashtag
 * @param createdAt instant the hashtag row was created
 */
public record HashtagIndexUpsertEvent(
        UUID eventId,
        Integer version,
        OffsetDateTime occurredAt,
        UUID hashtagId,
        String name,
        int postCount,
        OffsetDateTime createdAt) {}
