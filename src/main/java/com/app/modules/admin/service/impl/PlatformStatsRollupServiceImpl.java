package com.app.modules.admin.service.impl;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.admin.config.StatsProperties;
import com.app.modules.admin.enums.StatGranularity;
import com.app.modules.admin.repository.PlatformStatsRepository;
import com.app.modules.admin.service.PlatformStatsRollupService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PlatformStatsRollupServiceImpl implements PlatformStatsRollupService {

    private final PlatformStatsRepository platformStatsRepository;
    private final StatsProperties properties;

    public PlatformStatsRollupServiceImpl(
            PlatformStatsRepository platformStatsRepository, StatsProperties properties) {
        this.platformStatsRepository = platformStatsRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public int rollUpDay(OffsetDateTime dayStart) {
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        int written =
                platformStatsRepository.rollUpDay(dayStart, dayEnd, StatGranularity.HALF_HOUR);
        int removed =
                platformStatsRepository.deleteFineBuckets(
                        dayStart, dayEnd, StatGranularity.HALF_HOUR);
        log.info(
                "PlatformStatsRollup: compacted {} into {} daily rows, removed {} fine buckets",
                dayStart,
                written,
                removed);
        return written;
    }

    @Override
    @Transactional
    public int pruneDailyRows(OffsetDateTime now) {
        return platformStatsRepository.deleteDailyRowsBefore(
                now.minus(properties.dailyRetention()));
    }
}
