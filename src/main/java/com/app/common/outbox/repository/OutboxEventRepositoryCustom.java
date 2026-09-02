package com.app.common.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.app.common.outbox.entity.OutboxEvent;

public interface OutboxEventRepositoryCustom {

    OutboxEvent insertPending(OutboxEvent event);

    /**
     * Inserts a pending event, ignoring a row whose {@code event_id} is already present.
     *
     * @param event the event to enqueue, carrying a caller-supplied event id
     * @return the inserted event, or empty when that event id was already enqueued
     */
    Optional<OutboxEvent> insertPendingIgnoreDuplicate(OutboxEvent event);

    List<OutboxEvent> claimPublishableBatch(
            OffsetDateTime now,
            OffsetDateTime claimedAt,
            OffsetDateTime claimedUntil,
            int batchSize);

    boolean markPublished(UUID id, UUID eventId, UUID claimId, OffsetDateTime publishedAt);

    boolean markFailed(
            UUID id,
            UUID eventId,
            UUID claimId,
            int attemptCount,
            OffsetDateTime nextRetryAt,
            String lastError);

    boolean markDead(
            UUID id,
            UUID eventId,
            UUID claimId,
            int attemptCount,
            OffsetDateTime deadAt,
            String lastError);

    /**
     * Deletes at most {@code batchSize} published rows whose {@code published_at} precedes the
     * cutoff.
     *
     * <p>Only {@code PUBLISHED} rows are eligible. {@code PENDING} and {@code PROCESSING} rows are
     * still in flight, and a {@code DEAD} row is the only record that a domain event was
     * permanently lost, so none of the three is ever a deletion candidate.
     *
     * @param cutoff rows published strictly before this instant are eligible
     * @param batchSize maximum rows to delete in this statement
     * @return the number of rows actually deleted, which the caller uses to detect the last batch
     */
    int deletePublishedBefore(OffsetDateTime cutoff, int batchSize);
}
