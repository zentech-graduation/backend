package com.app.common.devseed;

/** Populates a development database with a realistic dataset for manual review. */
public interface DevDataSeedService {

    /**
     * Seeds curated users, posts with real (externally hosted) media, comments, likes, saves,
     * follows, and a single reviewer account that follows every seeded user.
     *
     * <p>Idempotent: if the reviewer account already exists the database is treated as already
     * seeded and nothing is written. Denormalised counters are left to the database triggers; this
     * method only inserts into their source tables.
     *
     * @return the outcome, including the reviewer credentials to surface in the log
     */
    DevSeedResult seed();
}
