package com.app.modules.admin.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.modules.admin.config.StatsProperties;
import com.app.modules.admin.service.PlatformStatsCollectionService;
import com.app.modules.admin.service.StatsBuckets;

import lombok.extern.slf4j.Slf4j;

/**
 * Fills {@code platform_stats} one completed bucket at a time.
 *
 * <p>Only buckets that have already ended are written, and the bucket the process started inside is
 * never written at all. A partial bucket records a fraction of an interval's activity as though it
 * were a whole one, and since nothing backfills this table that artificially low point would sit at
 * the left edge of every chart for as long as the data is kept.
 *
 * <p>Each pass writes every complete bucket it has not yet written rather than only the most recent
 * one, so a delayed or missed pass leaves no hole. A pass is capped so that returning from a long
 * outage does not attempt an unbounded amount of work in one transaction; the remaining buckets are
 * picked up by the passes that follow.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.stats",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StatsCollectionJob {

    /** Most buckets one pass will fill, so a long outage is caught up over several passes. */
    private static final int MAX_BUCKETS_PER_PASS = 48;

    private final PlatformStatsCollectionService collectionService;
    private final StatsProperties properties;
    private final OffsetDateTime firstFullBucket;

    private OffsetDateTime lastWrittenBucket;

    @Autowired
    public StatsCollectionJob(
            PlatformStatsCollectionService collectionService, StatsProperties properties) {
        this(collectionService, properties, Instant.now());
    }

    // The start instant is a constructor argument rather than read inside the job so the
    // partial-bucket rule can be exercised without waiting for a real half hour to elapse.
    StatsCollectionJob(
            PlatformStatsCollectionService collectionService,
            StatsProperties properties,
            Instant startedAt) {
        this.collectionService = collectionService;
        this.properties = properties;
        this.firstFullBucket = StatsBuckets.firstFullBucket(startedAt, properties.interval());
    }

    @Scheduled(
            initialDelayString = "${app.stats.interval:PT30M}",
            fixedDelayString = "${app.stats.interval:PT30M}")
    public void collect() {
        collectDue(Instant.now());
    }

    /**
     * Writes every complete bucket not yet written, subject to the partial-bucket rule and the
     * per-pass cap.
     *
     * @param now the instant to measure completeness against
     * @return number of buckets written
     */
    int collectDue(Instant now) {
        OffsetDateTime newest = StatsBuckets.lastCompleteBucket(now, properties.interval());
        OffsetDateTime next =
                lastWrittenBucket == null
                        ? firstFullBucket
                        : lastWrittenBucket.plus(properties.interval());
        if (next.isAfter(newest)) {
            return 0;
        }
        int written = 0;
        while (!next.isAfter(newest) && written < MAX_BUCKETS_PER_PASS) {
            collectionService.collectBucket(next);
            lastWrittenBucket = next;
            next = next.plus(properties.interval());
            written++;
        }
        log.info("StatsCollectionJob: wrote {} bucket(s) up to {}", written, lastWrittenBucket);
        return written;
    }
}
