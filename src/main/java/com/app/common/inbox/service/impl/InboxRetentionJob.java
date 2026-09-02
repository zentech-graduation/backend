package com.app.common.inbox.service.impl;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.common.inbox.config.InboxRetentionProperties;
import com.app.common.inbox.repository.ProcessedMessageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically deletes {@code processed_messages} rows older than the configured retention window.
 *
 * <p>Without this job the table grows monotonically, and its {@code (consumer_name, event_id)}
 * unique index is consulted on every consumed message, so the idempotency check gets steadily more
 * expensive on the hot path.
 *
 * <p>The retention window is a correctness boundary rather than a housekeeping preference. Each row
 * is the only thing preventing a redelivered message from re-running its side effects, so the
 * window must exceed every path by which the same event can arrive twice. See {@link
 * InboxRetentionProperties#getProcessedRetention()} for the computed worst case and the margin
 * chosen over it.
 */
@Slf4j
@Component
public class InboxRetentionJob {

    private final ProcessedMessageRepository repository;
    private final InboxRetentionProperties properties;

    public InboxRetentionJob(
            ProcessedMessageRepository repository, InboxRetentionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    // Not @Transactional for the same reason as OutboxRetentionJob: one transaction around the loop
    // would hold every row lock the run takes until the last batch committed.
    //
    // The initial delay is staggered past the outbox job's so the two do not contend for the same
    // connection pool slots on a cold start.
    @Scheduled(
            initialDelayString = "${app.inbox.retention.initial-delay:PT15M}",
            fixedDelayString = "${app.inbox.retention.fixed-delay:PT6H}")
    public void purge() {
        if (!properties.isEnabled()) {
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.resolvedProcessedRetention());
        int batchSize = properties.resolvedBatchSize();
        int ceiling = properties.resolvedMaxRowsPerRun();

        int deletedTotal = 0;
        while (deletedTotal < ceiling) {
            int limit = Math.min(batchSize, ceiling - deletedTotal);
            int deleted = repository.deleteProcessedBefore(cutoff, limit);
            deletedTotal += deleted;

            if (deleted < limit) {
                break;
            }
        }

        if (deletedTotal > 0) {
            log.info(
                    "InboxRetentionJob: deleted {} idempotency rows (processedBefore={}, ceiling={})",
                    deletedTotal,
                    cutoff,
                    ceiling);
        }
        if (deletedTotal >= ceiling) {
            log.warn(
                    "InboxRetentionJob: hit the per-run ceiling of {} rows, backlog remains for the"
                            + " next run",
                    ceiling);
        }
    }
}
