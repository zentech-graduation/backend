-- Flyway migration V28
-- Recommendation module prerequisite: extend user_events monthly partitions through 2027-06.
-- The default partition (user_events_default) has been catching rows since 2026-07 because
-- no partition existed past 2026-06. This migration detaches the default, creates the missing
-- monthly partitions, relocates the leaked rows into the proper partitions, and re-attaches
-- the default partition so future overflow is still contained.

-- Detach the default partition so the new range partitions can be created without overlap.
ALTER TABLE user_events DETACH PARTITION user_events_default;

CREATE TABLE user_events_2026_07 PARTITION OF user_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE user_events_2026_08 PARTITION OF user_events
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE user_events_2026_09 PARTITION OF user_events
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE user_events_2026_10 PARTITION OF user_events
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE user_events_2026_11 PARTITION OF user_events
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE user_events_2026_12 PARTITION OF user_events
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE user_events_2027_01 PARTITION OF user_events
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE user_events_2027_02 PARTITION OF user_events
    FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE user_events_2027_03 PARTITION OF user_events
    FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE user_events_2027_04 PARTITION OF user_events
    FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE user_events_2027_05 PARTITION OF user_events
    FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE user_events_2027_06 PARTITION OF user_events
    FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');

-- Relocate rows that leaked into the default partition back into their proper partitions.
-- user_events_default is now a standalone table, so this is a plain cross-table move.
INSERT INTO user_events (id, user_id, session_id, event_type, entity_type, entity_id,
                         metadata, ip_address, user_agent, platform, created_at)
SELECT id, user_id, session_id, event_type, entity_type, entity_id,
       metadata, ip_address, user_agent, platform, created_at
FROM user_events_default
WHERE created_at >= '2026-07-01' AND created_at < '2027-07-01';

DELETE FROM user_events_default
WHERE created_at >= '2026-07-01' AND created_at < '2027-07-01';

-- Re-attach the default partition so overflow beyond 2027-07 is still contained.
ALTER TABLE user_events ATTACH PARTITION user_events_default DEFAULT;
