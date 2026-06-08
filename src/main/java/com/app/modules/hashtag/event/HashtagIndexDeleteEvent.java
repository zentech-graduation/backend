package com.app.modules.hashtag.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload for a hashtag index delete event consumed by the search-index sync worker.
 *
 * <p>Instructs the worker to remove the corresponding Elasticsearch document.
 *
 * @param eventId unique identifier of this event instance
 * @param version schema version of the event payload
 * @param occurredAt instant the source state change occurred
 * @param hashtagId identifier of the hashtag to remove from the index
 */
public record HashtagIndexDeleteEvent(
        UUID eventId, int version, OffsetDateTime occurredAt, UUID hashtagId) {}
