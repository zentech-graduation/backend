package com.app.modules.admin.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Bucket boundary arithmetic for the statistics collection job.
 *
 * <p>Buckets are aligned to the epoch rather than to when the process started, so the same wall
 * clock instant always belongs to the same bucket no matter when or how often the job restarts. Two
 * runs of the job on two days therefore agree about where a boundary is, which is what makes the
 * daily roll-up able to assume a whole number of buckets per day.
 */
public final class StatsBuckets {

    private StatsBuckets() {}

    /**
     * Returns the start of the bucket containing {@code instant}.
     *
     * @param instant any point in time
     * @param interval bucket width; must be positive
     * @return the epoch-aligned bucket start at or before {@code instant}
     */
    public static OffsetDateTime floor(Instant instant, Duration interval) {
        long width = interval.toMillis();
        long millis = instant.toEpochMilli();
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(Math.floorDiv(millis, width) * width), ZoneOffset.UTC);
    }

    /**
     * Returns the start of the first bucket that is entirely after {@code instant}.
     *
     * <p>This is the earliest bucket a process starting at {@code instant} observed from its very
     * beginning. Writing the bucket the process started inside would record a fraction of an
     * interval's activity as if it were a whole one, and that artificially low point would sit at
     * the left edge of every chart forever, because there is no backfill to correct it with.
     *
     * @param instant the process start
     * @param interval bucket width; must be positive
     * @return the first fully observed bucket start
     */
    public static OffsetDateTime firstFullBucket(Instant instant, Duration interval) {
        OffsetDateTime floored = floor(instant, interval);
        return floored.toInstant().equals(instant) ? floored : floored.plus(interval);
    }

    /**
     * Returns the start of the most recent bucket that has already ended at {@code instant}.
     *
     * @param instant any point in time
     * @param interval bucket width; must be positive
     * @return the bucket start immediately before the one containing {@code instant}
     */
    public static OffsetDateTime lastCompleteBucket(Instant instant, Duration interval) {
        return floor(instant, interval).minus(interval);
    }
}
