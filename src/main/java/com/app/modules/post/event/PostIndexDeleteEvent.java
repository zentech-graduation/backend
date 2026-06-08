package com.app.modules.post.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload for a post index delete event consumed by the search-index sync worker.
 *
 * <p>Instructs the worker to remove the corresponding Elasticsearch document.
 *
 * @param eventId unique identifier of this event instance
 * @param version schema version of the event payload
 * @param occurredAt instant the source state change occurred
 * @param postId identifier of the post to remove from the index
 */
public record PostIndexDeleteEvent(
        UUID eventId, int version, OffsetDateTime occurredAt, UUID postId) {}
