-- The aggregate the administrative statistics surface reads.
--
-- Long format rather than wide. Most of the metrics are dimensional breakdowns - users by status,
-- users by role, reports by status, reports by reason, admin actions by type - and a wide table
-- would need a schema migration every time one of those enums gains a value. A row per
-- (metric, dimension) needs none.
--
-- The composite primary key is the idempotency guard. Re-running the collection job for a bucket
-- upserts rather than duplicating, which matters because the job will be re-run after any incident.
--
-- Two kinds of metric share the table and must never be aggregated the same way.
--
--   Gauges are absolute snapshots bounded by the end of the bucket: total users, the distribution
--   by status and by role, total posts, total comments, total stories, reports by status and by
--   reason. Bounding them by the bucket end rather than snapshotting whatever is true when the job
--   happens to run is what makes a re-run for a past bucket reproduce the number it wrote the first
--   time, instead of overwriting history with today's state.
--
--   Flows are direct counts over the bucket window: registrations, posts, comments, follows, likes,
--   admin actions by type. They are never derived by subtracting consecutive gauge snapshots. A
--   deletion makes such a difference negative, so "new posts this interval" would read as -3 after
--   a moderation sweep, and a missed job run would silently fold two intervals of activity into one
--   bucket with no way to detect it afterwards. A direct range count has neither problem.
--
-- Rolling half_hour rows up to day sums the flows and takes the last bucket for the gauges. Summing
-- 48 snapshots of "total users" produces a number 48 times too large, and it looks plausible enough
-- to ship, which is why it is written down here as well as in the job.
--
-- A dimension absent from a bucket means zero. GROUP BY produces no row for an empty group, so a
-- status nobody holds simply has no row rather than a row holding 0, and every reader treats a
-- missing dimension as zero.
--
-- CREATE TYPE shares this file with the table that first uses it. That is allowed inside a
-- transaction; only ALTER TYPE ... ADD VALUE is not, and this migration adds no value to an
-- existing type.
CREATE TYPE stat_granularity AS ENUM ('half_hour', 'day');

CREATE TABLE platform_stats (
    bucket_start TIMESTAMPTZ      NOT NULL,
    granularity  stat_granularity NOT NULL,
    metric_key   VARCHAR(64)      NOT NULL,
    dimension    VARCHAR(64)      NOT NULL DEFAULT '',
    value        BIGINT           NOT NULL,
    computed_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (bucket_start, granularity, metric_key, dimension)
);
