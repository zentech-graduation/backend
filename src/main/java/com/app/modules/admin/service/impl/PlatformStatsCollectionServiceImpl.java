package com.app.modules.admin.service.impl;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.admin.config.StatsProperties;
import com.app.modules.admin.enums.PlatformMetric;
import com.app.modules.admin.enums.StatGranularity;
import com.app.modules.admin.repository.PlatformStatsRepository;
import com.app.modules.admin.service.PlatformStatsCollectionService;

@Service
public class PlatformStatsCollectionServiceImpl implements PlatformStatsCollectionService {

    private final PlatformStatsRepository platformStatsRepository;
    private final StatsProperties properties;

    public PlatformStatsCollectionServiceImpl(
            PlatformStatsRepository platformStatsRepository, StatsProperties properties) {
        this.platformStatsRepository = platformStatsRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public int collectBucket(OffsetDateTime bucketStart) {
        OffsetDateTime bucketEnd = bucketStart.plus(properties.interval());
        int written = 0;
        for (PlatformMetric metric : PlatformMetric.values()) {
            written +=
                    platformStatsRepository.collect(
                            metric, StatGranularity.HALF_HOUR, bucketStart, bucketEnd);
        }
        return written;
    }
}
