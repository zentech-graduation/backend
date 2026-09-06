package com.app.common.outbox.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.app.common.outbox.entity.OutboxEvent;

/** Service API for short transactional state changes used by the outbox publisher. */
public interface OutboxPublisherStateService {

    /**
     * Claims due outbox events for publishing with a bounded processing lease.
     *
     * @param now current UTC timestamp used for due-event filtering and lease start
     * @param batchSize maximum number of events to claim
     * @return claimed events owned by this publisher attempt
     */
    List<OutboxEvent> claimPublishableBatch(OffsetDateTime now, int batchSize);

    /**
     * Marks a claimed event as published.
     *
     * @param event claimed event whose RabbitMQ publish was confirmed
     * @param publishedAt UTC timestamp when broker acceptance was recorded
     * @return {@code true} when the claimed row was updated
     */
    boolean markPublished(OutboxEvent event, OffsetDateTime publishedAt);

    /**
     * Marks a claimed event as failed and schedules its next retry.
     *
     * @param event claimed event whose publish failed
     * @param attemptCount publish failure count to persist
     * @param nextRetryAt UTC timestamp when the event can be reclaimed
     * @param lastError sanitized failure summary
     * @return {@code true} when the claimed row was updated
     */
    boolean markFailed(
            OutboxEvent event, int attemptCount, OffsetDateTime nextRetryAt, String lastError);

    /**
     * Marks a claimed event as permanently dead after retry exhaustion.
     *
     * @param event claimed event whose retry budget was exhausted
     * @param attemptCount final publish failure count to persist
     * @param deadAt UTC timestamp when the terminal state was recorded
     * @param lastError sanitized failure summary
     * @return {@code true} when the claimed row was updated
     */
    boolean markDead(OutboxEvent event, int attemptCount, OffsetDateTime deadAt, String lastError);
}
