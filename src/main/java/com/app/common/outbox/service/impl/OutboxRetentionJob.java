package com.app.common.outbox.service.impl;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.common.outbox.config.OutboxRetentionProperties;
import com.app.common.outbox.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically deletes {@code outbox_events} rows that were published longer ago than the
 * configured retention window.
 *
 * <p>Without this job the table grows monotonically. Every domain event inserts a row and nothing
 * ever removes one, so {@code outbox_events_event_id_key} keeps growing while the publisher
 * consults it on every publish.
 *
 * <p>Only {@code PUBLISHED} rows are eligible. {@code DEAD} rows are retained indefinitely because
 * each one is the sole record that a domain event was permanently lost, and {@code PENDING} and
 * {@code PROCESSING} rows are still in flight.
 */
@Slf4j
@Component
public class OutboxRetentionJob {

    private final OutboxEventRepository repository;
    private final OutboxRetentionProperties properties;

    public OutboxRetentionJob(
            OutboxEventRepository repository, OutboxRetentionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    // Deliberately not @Transactional, which is the one place this job diverges from
    // RefreshTokenPurgeJob. Wrapping the loop in a single transaction would hold every row lock the
    // run takes until the final batch committed, which is precisely the unbounded lock footprint
    // batching exists to avoid. Each statement commits on its own instead, so an interrupted run
    // keeps the work it already did.
    @Scheduled(
            initialDelayString = "${app.outbox.retention.initial-delay:PT10M}",
            fixedDelayString = "${app.outbox.retention.fixed-delay:PT6H}")
    public void purge() {
        if (!properties.isEnabled()) {
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minus(properties.resolvedPublishedRetention());
        int batchSize = properties.resolvedBatchSize();
        int ceiling = properties.resolvedMaxRowsPerRun();

        int deletedTotal = 0;
        while (deletedTotal < ceiling) {
            int limit = Math.min(batchSize, ceiling - deletedTotal);
            int deleted = repository.deletePublishedBefore(cutoff, limit);
            deletedTotal += deleted;

            // A short batch means the eligible set is exhausted. Checking the count rather than
            // re-querying keeps the loop to one statement per batch.
            if (deleted < limit) {
                break;
            }
        }

        if (deletedTotal > 0) {
            log.info(
                    "OutboxRetentionJob: deleted {} published rows (publishedBefore={}, ceiling={})",
                    deletedTotal,
                    cutoff,
                    ceiling);
        }
        if (deletedTotal >= ceiling) {
            log.warn(
                    "OutboxRetentionJob: hit the per-run ceiling of {} rows, backlog remains for the"
                            + " next run",
                    ceiling);
        }
    }
}
