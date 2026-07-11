-- Flyway migration V29
-- Recommendation module: impressions table.
-- Records every post impression served by the explore feed for CTR analysis and position-bias
-- correction. Partitioned by month mirroring user_events; retention is enforced by the
-- RecommendationPartitionMaintenanceJob dropping whole partitions past the configured window.
-- post_id intentionally has no FK so audit/training history survives post hard-delete,
-- mirroring user_events.entity_id (see plan section 1.1 and DATA_RULES recommendation).

CREATE TABLE impressions (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id      UUID        NOT NULL,
    source       VARCHAR(16) NOT NULL CHECK (source IN
                   ('cf','content','graph','trending','explore_slot','fallback')),
    position     SMALLINT    NOT NULL CHECK (position >= 0),
    request_id   UUID        NOT NULL,
    blend_score  DECIMAL(10,4),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- One loop instead of twelve hand-written statements; format() quotes the range bounds and
-- to_char() derives the partition name, so adding months means changing one bound only.
DO $$
DECLARE
    month_start date;
BEGIN
    FOR month_start IN
        SELECT generate_series(date '2026-07-01', date '2027-06-01', interval '1 month')::date
    LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS impressions_%s PARTITION OF impressions'
            ' FOR VALUES FROM (%L) TO (%L)',
            to_char(month_start, 'YYYY_MM'),
            month_start,
            (month_start + interval '1 month')::date);
    END LOOP;
END $$;

CREATE TABLE impressions_default PARTITION OF impressions DEFAULT;

CREATE INDEX idx_impressions_user   ON impressions (user_id, created_at DESC);
CREATE INDEX idx_impressions_source ON impressions (source, created_at DESC);
CREATE INDEX idx_impressions_post   ON impressions (post_id, created_at DESC);
