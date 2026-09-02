package com.app.common.outbox.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Binds scheduled outbox retention settings from {@code app.outbox.retention.*}. */
@ConfigurationProperties(prefix = "app.outbox.retention")
@Getter
@Setter
public class OutboxRetentionProperties {

    private boolean enabled = true;

    /**
     * How long a published row is kept before it becomes eligible for deletion.
     *
     * <p>A published row has no operational use once its consumers have recorded it in {@code
     * processed_messages}, so this window exists only for forensics. It is deliberately long rather
     * than minimal: reading a published row costs nothing, and losing the ability to trace an event
     * during an incident costs a great deal.
     */
    private Duration publishedRetention = Duration.ofDays(30);

    /**
     * Rows deleted per statement.
     *
     * <p>Bounds the row locks any single delete holds against a table that every domain event
     * writes to.
     */
    private int batchSize = 500;

    /**
     * Upper bound on rows deleted across all batches in one run.
     *
     * <p>Guarantees the job terminates. Without it a first run against a long-neglected table would
     * loop until the backlog cleared, holding the scheduler thread for an unbounded period.
     */
    private int maxRowsPerRun = 50_000;

    public Duration resolvedPublishedRetention() {
        if (publishedRetention == null
                || publishedRetention.isNegative()
                || publishedRetention.isZero()) {
            return Duration.ofDays(30);
        }
        return publishedRetention;
    }

    public int resolvedBatchSize() {
        return batchSize <= 0 ? 500 : batchSize;
    }

    public int resolvedMaxRowsPerRun() {
        return maxRowsPerRun <= 0 ? 50_000 : maxRowsPerRun;
    }
}
