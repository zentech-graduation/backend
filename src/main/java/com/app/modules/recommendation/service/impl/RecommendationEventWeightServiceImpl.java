package com.app.modules.recommendation.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.app.modules.recommendation.config.RecommendationProperties;
import com.app.modules.recommendation.entity.RecommendationEventWeight;
import com.app.modules.recommendation.enums.RecommendationEventType;
import com.app.modules.recommendation.repository.RecommendationEventWeightRepository;
import com.app.modules.recommendation.service.RecommendationEventWeightService;

@Service
public class RecommendationEventWeightServiceImpl implements RecommendationEventWeightService {

    private static final Logger log =
            LoggerFactory.getLogger(RecommendationEventWeightServiceImpl.class);

    private final RecommendationEventWeightRepository repository;
    private final RecommendationProperties properties;

    // Volatile snapshot: a read of the map and its contained sets is atomic after a refresh.
    // A read may briefly observe the previous snapshot while a refresh is in flight, which is
    // acceptable for ranking metadata that changes rarely and only shifts weight tuning.
    private volatile Snapshot snapshot = Snapshot.empty();

    // Last refresh timestamp guarded by a synchronized refresh() so the scheduled job and explicit
    // refresh() do not race on staleness checks.
    private volatile Instant lastRefreshAt = Instant.EPOCH;

    public RecommendationEventWeightServiceImpl(
            RecommendationEventWeightRepository repository, RecommendationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public BigDecimal weight(RecommendationEventType eventType) {
        ensureFresh();
        // A missing row means the event carries no recommendation signal; zero keeps callers free
        // of null checks when a new enum value has not been seeded yet.
        return snapshot.weights.getOrDefault(eventType, BigDecimal.ZERO);
    }

    @Override
    public Set<RecommendationEventType> cfEnabled() {
        ensureFresh();
        return snapshot.cfEnabled;
    }

    @Override
    public Set<RecommendationEventType> trendingEnabled() {
        ensureFresh();
        return snapshot.trendingEnabled;
    }

    @Override
    public Set<RecommendationEventType> affinityEnabled() {
        ensureFresh();
        return snapshot.affinityEnabled;
    }

    @Override
    @Scheduled(fixedDelayString = "${app.recommendation.weights.refresh-interval:PT5M}")
    public synchronized void refresh() {
        try {
            snapshot = loadSnapshot();
            lastRefreshAt = Instant.now();
        } catch (RuntimeException ex) {
            // Keep the previous snapshot on failure so a transient DB blip cannot blank out ranking
            // metadata; the next scheduled refresh retries.
            log.warn("Failed to refresh recommendation event weights: {}", ex.getMessage());
        }
    }

    private Snapshot loadSnapshot() {
        Map<RecommendationEventType, BigDecimal> weights =
                new EnumMap<>(RecommendationEventType.class);
        Set<RecommendationEventType> cf = new HashSet<>();
        Set<RecommendationEventType> trending = new HashSet<>();
        Set<RecommendationEventType> affinity = new HashSet<>();
        for (RecommendationEventWeight row : repository.findAll()) {
            weights.put(row.getEventType(), row.getWeight());
            if (row.isCfEnabled()) {
                cf.add(row.getEventType());
            }
            if (row.isTrendingEnabled()) {
                trending.add(row.getEventType());
            }
            if (row.isAffinityEnabled()) {
                affinity.add(row.getEventType());
            }
        }
        return new Snapshot(
                Collections.unmodifiableMap(weights),
                Collections.unmodifiableSet(cf),
                Collections.unmodifiableSet(trending),
                Collections.unmodifiableSet(affinity));
    }

    private void ensureFresh() {
        Duration staleness = properties.getWeights().getRefreshInterval();
        if (lastRefreshAt.equals(Instant.EPOCH)
                || Duration.between(lastRefreshAt, Instant.now()).compareTo(staleness) > 0) {
            refresh();
        }
    }

    private record Snapshot(
            Map<RecommendationEventType, BigDecimal> weights,
            Set<RecommendationEventType> cfEnabled,
            Set<RecommendationEventType> trendingEnabled,
            Set<RecommendationEventType> affinityEnabled) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Set.of(), Set.of(), Set.of());
        }
    }
}
