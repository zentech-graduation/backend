package com.app.modules.hashtag.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload for a hashtag index upsert event consumed by the search-index sync worker.
 *
 * <p>Carries only the hashtag identifier; the consumer reads the canonical name, post count, and
 * creation timestamp from the source-of-truth database to materialize or refresh the corresponding
 * Elasticsearch document.
 *
 * @param eventId unique identifier of this event instance
 * @param version schema version of the event payload
 * @param occurredAt instant the source state change occurred
 * @param hashtagId identifier of the hashtag to index
 */
public record HashtagIndexUpsertEvent(
        UUID eventId, Integer version, OffsetDateTime occurredAt, UUID hashtagId) {}
