package com.app.modules.admin.service;

import java.time.OffsetDateTime;

/** Computes and stores one fine-grained bucket of platform statistics. */
public interface PlatformStatsCollectionService {

    /**
     * Computes every metric for one bucket and upserts the results.
     *
     * <p>Idempotent by construction. Gauges are bounded by the bucket end and flows by both edges,
     * so re-running for a past bucket reproduces the numbers it wrote the first time rather than
     * overwriting them with the present. That is what makes re-running after an incident safe.
     *
     * <p>Only ever writes at the fine granularity. Daily rows exist solely as the output of the
     * roll-up, so there is no way to ask this for one and get a bucket end computed from the wrong
     * width.
     *
     * @param bucketStart inclusive start of the bucket
     * @return number of dimension rows written across every metric
     */
    int collectBucket(OffsetDateTime bucketStart);
}
