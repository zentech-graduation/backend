package com.app.modules.recommendation.service.impl;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the {@code user_events} range-partitioned table ahead of incoming data by pre-creating the
 * monthly partition two months out on a rolling schedule.
 *
 * <p>Without this job the pre-seeded partitions eventually run out and every new event lands in the
 * {@code user_events_default} catch-all partition, which defeats partition pruning and grows
 * unbounded. The {@code DEFAULT} partition is intentionally left in place as a safety net so an
 * event whose timestamp outruns the pre-created partitions is never rejected; if {@code
 * user_events_default} ever accumulates rows it signals this job has fallen behind and should be
 * alerted on. Dropping old partitions (retention) is deliberately out of scope for this job.
 */
@Slf4j
@Component
public class UserEventsPartitionJob {

    private static final DateTimeFormatter PARTITION_SUFFIX =
            DateTimeFormatter.ofPattern("yyyy_MM");

    private final JdbcTemplate jdbcTemplate;

    public UserEventsPartitionJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ensures the partition two months ahead of the current month exists. Runs at 00:05 UTC on the
     * first day of every month; the non-midnight minute avoids racing any midnight boundary work.
     */
    @Scheduled(cron = "${app.recommendation.user-events-partition.cron:0 5 0 1 * *}", zone = "UTC")
    public void ensureUpcomingPartition() {
        LocalDate monthAfterNext = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(2);
        createMonthlyPartitionIfAbsent(monthAfterNext);
    }

    void createMonthlyPartitionIfAbsent(LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        String partitionName = "user_events_" + start.format(PARTITION_SUFFIX);
        String ddl =
                String.format(
                        "CREATE TABLE IF NOT EXISTS %s PARTITION OF user_events "
                                + "FOR VALUES FROM ('%s') TO ('%s')",
                        partitionName, start, end);
        jdbcTemplate.execute(ddl);
        log.info(
                "UserEventsPartitionJob: ensured partition {} for [{}, {})",
                partitionName,
                start,
                end);
    }
}
