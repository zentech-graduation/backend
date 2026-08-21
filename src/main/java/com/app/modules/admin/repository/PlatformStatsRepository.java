package com.app.modules.admin.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.app.modules.admin.enums.PlatformMetric;
import com.app.modules.admin.enums.StatGranularity;

/**
 * Writes and reads on {@code platform_stats}.
 *
 * <p>Every counting statement runs entirely inside the database. The counts are over the whole of
 * {@code users}, {@code posts} and {@code comments}, so bringing rows into the application to count
 * them there would move millions of rows across the wire to produce one number.
 */
public interface PlatformStatsRepository {

    /**
     * Computes one metric for one bucket and upserts every dimension it produces.
     *
     * @param metric which metric to compute
     * @param granularity bucket width to record it under
     * @param bucketStart inclusive start of the bucket
     * @param bucketEnd exclusive end of the bucket
     * @return number of dimension rows written
     */
    int collect(
            PlatformMetric metric,
            StatGranularity granularity,
            OffsetDateTime bucketStart,
            OffsetDateTime bucketEnd);

    /**
     * Rolls one day of fine-grained buckets into daily rows and removes the buckets.
     *
     * <p>Flow metrics are summed across the day's buckets and gauge metrics take the value of the
     * last bucket, which is the end-of-day state. Summing gauges would multiply a total by the
     * number of buckets in the day.
     *
     * <p>The aggregate and the delete run in the caller's transaction and in that order. Deleting
     * first would lose a day of history irrecoverably if the insert then failed, because nothing
     * backfills this table.
     *
     * @param dayStart inclusive start of the day, UTC midnight
     * @param dayEnd exclusive end of the day
     * @param fine bucket width being rolled up
     * @return number of daily rows written
     */
    int rollUpDay(OffsetDateTime dayStart, OffsetDateTime dayEnd, StatGranularity fine);

    /**
     * Deletes fine-grained buckets for one day, after {@link #rollUpDay} has written its daily
     * rows.
     *
     * @param dayStart inclusive start of the day
     * @param dayEnd exclusive end of the day
     * @param fine bucket width being removed
     * @return number of rows deleted
     */
    int deleteFineBuckets(OffsetDateTime dayStart, OffsetDateTime dayEnd, StatGranularity fine);

    /**
     * Lists the distinct days that still hold fine-grained buckets older than a cutoff.
     *
     * @param fine bucket width to look for
     * @param olderThan exclusive upper bound; only days entirely before this are returned
     * @return UTC midnights, oldest first
     */
    List<OffsetDateTime> findDaysWithFineBucketsBefore(
            StatGranularity fine, OffsetDateTime olderThan);

    /**
     * Deletes rolled-up daily rows older than a cutoff.
     *
     * @param olderThan exclusive upper bound
     * @return number of rows deleted
     */
    int deleteDailyRowsBefore(OffsetDateTime olderThan);

    /**
     * Returns the most recent fine-grained bucket that holds any row.
     *
     * @param granularity bucket width to look for
     * @return the newest bucket start, or empty when nothing has been collected yet
     */
    Optional<OffsetDateTime> findNewestBucket(StatGranularity granularity);

    /**
     * Reads every metric row belonging to one bucket.
     *
     * @param granularity bucket width
     * @param bucketStart the bucket to read
     * @return one entry per metric and dimension present in that bucket
     */
    List<StatRow> findBucket(StatGranularity granularity, OffsetDateTime bucketStart);

    /**
     * Reads one metric's series across a window, oldest first.
     *
     * @param metricKey metric to read
     * @param granularity bucket width to read at
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @return one entry per bucket and dimension in the window
     */
    List<StatRow> findSeries(
            String metricKey, StatGranularity granularity, OffsetDateTime from, OffsetDateTime to);

    /**
     * One stored statistic.
     *
     * @param bucketStart bucket the value belongs to
     * @param metricKey which metric
     * @param dimension breakdown key, empty when the metric has no breakdown
     * @param value the recorded number
     * @param computedAt when the job wrote it
     */
    record StatRow(
            OffsetDateTime bucketStart,
            String metricKey,
            String dimension,
            long value,
            OffsetDateTime computedAt) {}
}
