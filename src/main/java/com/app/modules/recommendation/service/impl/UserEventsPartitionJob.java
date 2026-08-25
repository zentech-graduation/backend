package com.app.modules.recommendation.service.impl;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the {@code user_events} range-partitioned table ahead of incoming data by maintaining a
 * rolling window of monthly partitions covering the current month and the next two.
 *
 * <p>Without this job the pre-seeded partitions eventually run out and every new event lands in the
 * {@code user_events_default} catch-all partition. That is worse than it sounds. The write still
 * succeeds, so nothing signals the problem, but the month's partition can never be created
 * afterwards: PostgreSQL refuses with "updated partition constraint for default partition would be
 * violated by some row". The gap becomes permanent and its rows become unprunable, because every
 * window-bounded query then has to read the default partition to prove it holds nothing relevant.
 *
 * <p>Two consequences shape the design. The job ensures the whole window rather than only its far
 * edge, so a run missed for any reason is repaired by the next one instead of leaving a hole that
 * outlives the outage. And it runs daily rather than monthly, so a process that is down on the
 * first of a month does not skip that month's creation entirely.
 *
 * <p>The {@code DEFAULT} partition is intentionally left in place as a safety net so an event whose
 * timestamp outruns the window is never rejected; if {@code user_events_default} ever accumulates
 * rows it signals this job has fallen behind and should be alerted on. Dropping old partitions
 * (retention) is deliberately out of scope for this job.
 */
@Slf4j
@Component
public class UserEventsPartitionJob {

    private static final DateTimeFormatter PARTITION_SUFFIX =
            DateTimeFormatter.ofPattern("yyyy_MM");

    /** Months ahead of the current one that must always be declared. */
    private static final int LEAD_MONTHS = 2;

    private final JdbcTemplate jdbcTemplate;

    public UserEventsPartitionJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ensures the current month and the next two exist as partitions.
     *
     * <p>Runs at 00:05 UTC every day; the non-midnight minute avoids racing any midnight boundary
     * work. Daily rather than monthly because a monthly schedule only fires on the first of the
     * month, so a process that is down that day skips a month it can never fill in later.
     */
    @Scheduled(cron = "${app.recommendation.user-events-partition.cron:0 5 0 * * *}", zone = "UTC")
    public void ensureUpcomingPartition() {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        for (int offset = 0; offset <= LEAD_MONTHS; offset++) {
            createMonthlyPartitionIfAbsent(currentMonth.plusMonths(offset));
        }
    }

    // PostgreSQL cannot bind a table identifier; the partition name derives only from LocalDate.
    @SuppressWarnings("java:S2077")
    void createMonthlyPartitionIfAbsent(LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        String partitionName = "user_events_" + start.format(PARTITION_SUFFIX);
        String ddl =
                String.format(
                        "CREATE TABLE IF NOT EXISTS %s PARTITION OF user_events "
                                + "FOR VALUES FROM ('%s') TO ('%s')",
                        partitionName, start, end);
        try {
            jdbcTemplate.execute(ddl);
        } catch (DataAccessException e) {
            // One month failing must not stop the others. The realistic cause is rows for this
            // month already sitting in user_events_default, which makes the partition uncreatable
            // until they are moved by hand, and that is a condition to alert on rather than one to
            // retry out of.
            log.warn(
                    "UserEventsPartitionJob: could not ensure partition {} for [{}, {})",
                    partitionName,
                    start,
                    end,
                    e);
            return;
        }
        log.info(
                "UserEventsPartitionJob: ensured partition {} for [{}, {})",
                partitionName,
                start,
                end);
    }
}
