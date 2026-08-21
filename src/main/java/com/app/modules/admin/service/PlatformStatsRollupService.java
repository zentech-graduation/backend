package com.app.modules.admin.service;

import java.time.OffsetDateTime;

/** Compacts fine-grained buckets into daily rows and enforces retention. */
public interface PlatformStatsRollupService {

    /**
     * Rolls one day of fine-grained buckets into daily rows and deletes the buckets.
     *
     * <p>Both steps run in one transaction and in that order. Deleting first and then failing to
     * insert would destroy a day of history with nothing able to reconstruct it, because this table
     * has no backfill: a bucket that was never collected can never be collected later.
     *
     * @param dayStart UTC midnight of the day to compact
     * @return number of daily rows written
     */
    int rollUpDay(OffsetDateTime dayStart);

    /**
     * Deletes rolled-up daily rows older than the configured retention.
     *
     * @param now the reference instant retention is measured back from
     * @return number of rows deleted
     */
    int pruneDailyRows(OffsetDateTime now);
}
