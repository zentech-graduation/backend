package com.app.common.outbox.service;

import java.util.Map;
import java.util.UUID;

import com.app.common.outbox.entity.OutboxEvent;

/** Service API for recording domain events into the transactional outbox. */
public interface OutboxService {

    /**
     * Enqueues a business event in the current transaction.
     *
     * @param eventType versioned business event type
     * @param routingKey RabbitMQ routing key for the event
     * @param aggregateType aggregate type that owns the event
     * @param aggregateId aggregate identifier that owns the event
     * @param actorId authenticated actor who caused the event, or {@code null} for system events
     * @param data small non-sensitive payload with identifiers and metadata only
     * @return persisted outbox event row
     */
    OutboxEvent enqueue(
            String eventType,
            String routingKey,
            String aggregateType,
            UUID aggregateId,
            UUID actorId,
            Map<String, Object> data);

    /**
     * Enqueues a business event under a caller-supplied event id, ignoring a repeat of that id.
     *
     * <p>For producers whose source already carries a stable identifier for the logical event, so
     * that a client retry resends the same id and is absorbed here rather than counted twice. The
     * id becomes the event id the whole downstream chain deduplicates on.
     *
     * @param eventId caller-owned identifier that makes this event unique
     * @param eventType versioned business event type
     * @param routingKey RabbitMQ routing key for the event
     * @param aggregateType aggregate type that owns the event
     * @param aggregateId aggregate identifier that owns the event
     * @param actorId authenticated actor who caused the event, or {@code null} for system events
     * @param data small non-sensitive payload with identifiers and metadata only
     * @return {@code true} when this call enqueued the event, {@code false} when it was already
     *     enqueued under that id
     */
    boolean enqueueOnce(
            UUID eventId,
            String eventType,
            String routingKey,
            String aggregateType,
            UUID aggregateId,
            UUID actorId,
            Map<String, Object> data);
}
