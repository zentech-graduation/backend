package com.app.common.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.common.outbox.entity.OutboxEvent;

public interface OutboxEventRepositoryCustom {

    OutboxEvent insertPending(OutboxEvent event);

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
}
