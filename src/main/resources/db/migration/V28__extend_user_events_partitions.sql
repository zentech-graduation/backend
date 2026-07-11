-- Flyway migration V28
-- Recommendation module prerequisite: extend user_events monthly partitions through 2027-06.
-- The default partition (user_events_default) has been catching rows since 2026-07 because
-- no partition existed past 2026-06. This migration detaches the default, creates the missing
-- monthly partitions, relocates the leaked rows into the proper partitions, and re-attaches
-- the default partition so future overflow is still contained.

-- Detach the default partition so the new range partitions can be created without overlap.
ALTER TABLE user_events DETACH PARTITION user_events_default;

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
            'CREATE TABLE IF NOT EXISTS user_events_%s PARTITION OF user_events'
            ' FOR VALUES FROM (%L) TO (%L)',
            to_char(month_start, 'YYYY_MM'),
            month_start,
            (month_start + interval '1 month')::date);
    END LOOP;
END $$;

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
