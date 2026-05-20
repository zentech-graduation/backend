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
}
