package com.app.modules.admin.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.modules.admin.config.StatsProperties;
import com.app.modules.admin.enums.StatGranularity;
import com.app.modules.admin.repository.PlatformStatsRepository;
import com.app.modules.admin.service.PlatformStatsRollupService;

import lombok.extern.slf4j.Slf4j;

/**
 * Compacts fine-grained buckets older than the fine retention into one daily row per metric, then
 * deletes daily rows past the daily retention.
 *
 * <p>Runs once a day. Each day is compacted in its own transaction so a failure part way through a
 * backlog leaves the days already done compacted and the rest untouched, rather than rolling back
 * work that had succeeded.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.stats",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StatsRollupJob {

    private final PlatformStatsRepository platformStatsRepository;
    private final PlatformStatsRollupService rollupService;
    private final StatsProperties properties;

    public StatsRollupJob(
            PlatformStatsRepository platformStatsRepository,
            PlatformStatsRollupService rollupService,
            StatsProperties properties) {
        this.platformStatsRepository = platformStatsRepository;
        this.rollupService = rollupService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.stats.rollup-cron:0 20 3 * * *}", zone = "UTC")
    public void rollUpAndPrune() {
        run(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Compacts every eligible day and then prunes expired daily rows.
     *
     * @param now the instant retention windows are measured back from
     * @return number of days compacted
     */
    int run(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minus(properties.fineRetention()).truncatedTo(ChronoUnit.DAYS);
        List<OffsetDateTime> days =
                platformStatsRepository.findDaysWithFineBucketsBefore(
                        StatGranularity.HALF_HOUR, cutoff);
        for (OffsetDateTime day : days) {
            rollupService.rollUpDay(day);
        }
        int pruned = rollupService.pruneDailyRows(now);
        log.info(
                "StatsRollupJob: compacted {} day(s), pruned {} daily row(s)", days.size(), pruned);
        return days.size();
    }
}
