package com.app.common.inbox.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Binds scheduled inbox retention settings from {@code app.inbox.retention.*}. */
@ConfigurationProperties(prefix = "app.inbox.retention")
@Getter
@Setter
public class InboxRetentionProperties {

    /**
     * How long a consumer idempotency record is kept.
     *
     * <p>This is the one value in the retention pair that carries correctness risk. A {@code
     * processed_messages} row is the only thing stopping a redelivered message from re-running its
     * side effects, so deleting one while its message can still arrive again reintroduces duplicate
     * processing.
     *
     * <p>Worst case for automatic redelivery, computed from configuration rather than assumed: the
     * outbox reclaims a stalled {@code PROCESSING} row after {@code
     * app.outbox.publisher.processing-timeout} (2m) and republishes through a three-attempt ladder
     * of 10s, 30s and 60s, while a consumer that fails transiently retries in-flight for 10s plus
     * 30s before dead-lettering. That totals roughly five minutes.
     *
     * <p>Thirty days is therefore about four orders of magnitude above the automatic worst case.
     * The margin is not sized for that case at all; it is sized for an operator replaying a
     * dead-lettered message by hand, which is bounded by how long a dead letter can go unnoticed
     * rather than by any timeout.
     */
    private Duration processedRetention = Duration.ofDays(30);

    private boolean enabled = true;

    /** Rows deleted per statement, bounding the row locks any single delete holds. */
    private int batchSize = 500;

    /**
     * Upper bound on rows deleted across all batches in one run.
     *
     * <p>Guarantees the job terminates rather than looping until a long-neglected backlog clears.
     */
    private int maxRowsPerRun = 50_000;

    public Duration resolvedProcessedRetention() {
        if (processedRetention == null
                || processedRetention.isNegative()
                || processedRetention.isZero()) {
            return Duration.ofDays(30);
        }
        return processedRetention;
    }

    public int resolvedBatchSize() {
        return batchSize <= 0 ? 500 : batchSize;
    }

    public int resolvedMaxRowsPerRun() {
        return maxRowsPerRun <= 0 ? 50_000 : maxRowsPerRun;
    }
}
