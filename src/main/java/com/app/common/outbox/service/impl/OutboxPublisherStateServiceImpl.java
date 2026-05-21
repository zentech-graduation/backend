package com.app.common.outbox.service.impl;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.outbox.config.OutboxPublisherProperties;
import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.repository.OutboxEventRepository;
import com.app.common.outbox.service.OutboxPublisherStateService;

@Service
public class OutboxPublisherStateServiceImpl implements OutboxPublisherStateService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisherProperties properties;

    public OutboxPublisherStateServiceImpl(
            OutboxEventRepository outboxEventRepository, OutboxPublisherProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public List<OutboxEvent> claimPublishableBatch(OffsetDateTime now, int batchSize) {
        return outboxEventRepository.claimPublishableBatch(
                now, now, now.plus(properties.resolvedProcessingTimeout()), batchSize);
    }

    @Override
    @Transactional
    public boolean markPublished(OutboxEvent event, OffsetDateTime publishedAt) {
        return outboxEventRepository.markPublished(
                event.getId(), event.getEventId(), event.getClaimId(), publishedAt);
    }

    @Override
    @Transactional
    public boolean markFailed(
            OutboxEvent event, int attemptCount, OffsetDateTime nextRetryAt, String lastError) {
        return outboxEventRepository.markFailed(
                event.getId(),
                event.getEventId(),
                event.getClaimId(),
                attemptCount,
                nextRetryAt,
                lastError);
    }

    @Override
    @Transactional
    public boolean markDead(
            OutboxEvent event, int attemptCount, OffsetDateTime deadAt, String lastError) {
        return outboxEventRepository.markDead(
                event.getId(),
                event.getEventId(),
                event.getClaimId(),
                attemptCount,
                deadAt,
                lastError);
    }
}
