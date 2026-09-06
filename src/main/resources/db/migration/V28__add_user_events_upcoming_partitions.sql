-- Create the current month's user_events partition plus the next two months, if absent.
--
-- V14 pre-created monthly partitions only through 2026-06 followed by a DEFAULT catch-all. Once
-- those run out, new events fall into user_events_default, which defeats partition pruning and grows
-- unbounded. This migration closes the immediate gap; UserEventsPartitionJob keeps the rolling
-- window ahead going forward. The DEFAULT partition is intentionally retained as a safety net.
DO $$
DECLARE
    month_start DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..2 LOOP
        month_start := date_trunc('month', CURRENT_DATE + (i * INTERVAL '1 month'))::date;
        partition_name := 'user_events_' || to_char(month_start, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF user_events '
                || 'FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            month_start,
            month_start + INTERVAL '1 month'
        );
    END LOOP;
END $$;
