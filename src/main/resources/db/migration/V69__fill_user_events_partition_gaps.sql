-- Close every gap in the declared monthly range of user_events, and keep the range contiguous.
--
-- V14 declared partitions from 2025-01 through 2026-06 and a DEFAULT catch-all. V28 then created
-- the month it ran in plus the next two. Any database migrated after 2026-07-31 therefore has a
-- permanent hole at 2026-07: V14 stops before it and V28 starts after it. The hole is invisible
-- until something writes into it, because the DEFAULT partition silently absorbs the row.
--
-- Measured on PostgreSQL 18.6 against this exact schema, because the behaviour is not what the
-- absence of a partition normally implies:
--
--   write into a declared month           -> lands in that month's partition
--   write into the 2026-07 hole           -> lands in user_events_default, no error
--   write past the last declared month    -> lands in user_events_default, no error
--   create the 2026-07 partition, default empty
--                                         -> succeeds
--   create it once a 2026-07 row sits in the default
--                                         -> ERROR: updated partition constraint for default
--                                            partition "user_events_default" would be violated by
--                                            some row
--   write into an undeclared month with the default detached
--                                         -> ERROR: no partition of relation "user_events" found
--
-- So a write never fails, and that is the problem. A row landing in the DEFAULT partition makes
-- that month's partition permanently uncreatable, and the row itself is then unprunable: every
-- query bounded by created_at has to read the default partition to prove it holds nothing relevant.
-- The hole has to be closed before anything writes to this table, which this branch is the first
-- thing in the project to do.
--
-- The lower bound is 2026-07, the month after V14's horizon. A deployment whose first migration run
-- happens long after that date creates some empty past-month partitions. That is intentional: a
-- contiguous declared range is what lets the planner discard the default partition for any window
-- inside it, and an empty partition costs a few pages.
--
-- The DEFAULT partition stays. It is the reason a write can never be rejected, which for analytics
-- data is the right trade; rows accumulating in it are the signal that the horizon has fallen
-- behind, not a reason to remove the safety net.
DO $$
DECLARE
    month_start    DATE;
    horizon        DATE;
    partition_name TEXT;
    trapped_rows   BIGINT;
BEGIN
    horizon := date_trunc('month', CURRENT_DATE + INTERVAL '2 months')::date;
    month_start := DATE '2026-07-01';

    WHILE month_start <= horizon LOOP
        partition_name := 'user_events_' || to_char(month_start, 'YYYY_MM');

        IF NOT EXISTS (
            SELECT 1 FROM pg_class WHERE relname = partition_name AND relkind = 'r'
        ) THEN
            EXECUTE format(
                'SELECT count(*) FROM user_events_default WHERE created_at >= %L AND created_at < %L',
                month_start,
                month_start + INTERVAL '1 month'
            ) INTO trapped_rows;

            IF trapped_rows > 0 THEN
                -- Creating the partition would abort the migration and with it the deployment, over
                -- an analytics table. Report it and continue; the rows stay readable in the default
                -- partition and an operator can move them by hand.
                RAISE NOTICE
                    'Skipping % : % row(s) for that month already sit in user_events_default and '
                        'must be moved before the partition can be created',
                    partition_name,
                    trapped_rows;
            ELSE
                EXECUTE format(
                    'CREATE TABLE %I PARTITION OF user_events FOR VALUES FROM (%L) TO (%L)',
                    partition_name,
                    month_start,
                    month_start + INTERVAL '1 month'
                );
            END IF;
        END IF;

        month_start := (month_start + INTERVAL '1 month')::date;
    END LOOP;
END $$;
