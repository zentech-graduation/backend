package com.app.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class StatsBucketsTest {

    private static final Duration HALF_HOUR = Duration.ofMinutes(30);

    @Test
    void floor_alignsToTheEpochNotToTheProcess() {
        assertThat(StatsBuckets.floor(Instant.parse("2026-08-19T10:17:42.123Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T10:00:00Z"));
        assertThat(StatsBuckets.floor(Instant.parse("2026-08-19T10:30:00Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T10:30:00Z"));
        assertThat(StatsBuckets.floor(Instant.parse("2026-08-19T10:59:59.999Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T10:30:00Z"));
    }

    @Test
    void floor_worksBeforeTheEpoch() {
        // Math.floorDiv rather than integer division, so a negative epoch millisecond rounds down
        // rather than towards zero. Nothing writes such a timestamp today, but an off-by-one bucket
        // boundary is exactly the kind of arithmetic that is wrong silently.
        assertThat(StatsBuckets.floor(Instant.parse("1969-12-31T23:44:00Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("1969-12-31T23:30:00Z"));
    }

    @Test
    void firstFullBucket_skipsTheBucketTheProcessStartedInside() {
        // Started 17 minutes into the 10:00 bucket, so 10:00 was only partly observed and must not
        // be written. There is no backfill, so an artificially low first point would sit at the
        // left edge of every chart for as long as the data is kept.
        assertThat(StatsBuckets.firstFullBucket(Instant.parse("2026-08-19T10:17:42Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T10:30:00Z"));
    }

    @Test
    void firstFullBucket_startingExactlyOnABoundaryKeepsThatBucket() {
        // Nothing of the bucket was missed, so skipping it would discard a complete interval.
        assertThat(StatsBuckets.firstFullBucket(Instant.parse("2026-08-19T10:30:00Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T10:30:00Z"));
    }

    @Test
    void lastCompleteBucket_isTheOneBeforeTheBucketInProgress() {
        assertThat(
                        StatsBuckets.lastCompleteBucket(
                                Instant.parse("2026-08-19T10:17:42Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T09:30:00Z"));
        // On a boundary the bucket that just ended is complete, and the new one has no content yet.
        assertThat(
                        StatsBuckets.lastCompleteBucket(
                                Instant.parse("2026-08-19T10:30:00Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T10:00:00Z"));
    }

    @Test
    void aDayHoldsAWholeNumberOfBuckets() {
        // The daily roll-up assumes it can sum a day's buckets without a partial one straddling
        // midnight, which holds only because buckets are epoch-aligned and divide a day evenly.
        assertThat(Duration.ofDays(1).toMillis() % HALF_HOUR.toMillis()).isZero();
        assertThat(StatsBuckets.floor(Instant.parse("2026-08-19T00:00:00Z"), HALF_HOUR))
                .isEqualTo(OffsetDateTime.parse("2026-08-19T00:00:00Z"));
    }
}
