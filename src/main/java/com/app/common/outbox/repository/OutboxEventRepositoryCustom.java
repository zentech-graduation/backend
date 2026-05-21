package com.app.common.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.common.outbox.entity.OutboxEvent;

public interface OutboxEventRepositoryCustom {

    OutboxEvent insertPending(OutboxEvent event);

    List<OutboxEvent> findPublishableBatch(OffsetDateTime now, int batchSize);

    void markPublished(UUID id, OffsetDateTime publishedAt);

    void markFailed(UUID id, int attemptCount, OffsetDateTime nextRetryAt, String lastError);

    void markDead(UUID id, int attemptCount, OffsetDateTime deadAt, String lastError);
}
