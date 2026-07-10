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

CREATE TABLE impressions_2026_07 PARTITION OF impressions
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE impressions_2026_08 PARTITION OF impressions
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE impressions_2026_09 PARTITION OF impressions
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE impressions_2026_10 PARTITION OF impressions
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE impressions_2026_11 PARTITION OF impressions
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE impressions_2026_12 PARTITION OF impressions
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE impressions_2027_01 PARTITION OF impressions
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE impressions_2027_02 PARTITION OF impressions
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE impressions_2027_03 PARTITION OF impressions
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE impressions_2027_04 PARTITION OF impressions
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE impressions_2027_05 PARTITION OF impressions
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE impressions_2027_06 PARTITION OF impressions
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE impressions_default PARTITION OF impressions DEFAULT;

CREATE INDEX idx_impressions_user   ON impressions (user_id, created_at DESC);
CREATE INDEX idx_impressions_source ON impressions (source, created_at DESC);
CREATE INDEX idx_impressions_post   ON impressions (post_id, created_at DESC);
