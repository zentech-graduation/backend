package com.app.modules.recommendation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.recommendation.config.RecommendationProperties;
import com.app.modules.recommendation.entity.RecommendationEventWeight;
import com.app.modules.recommendation.enums.RecommendationEventType;
import com.app.modules.recommendation.repository.RecommendationEventWeightRepository;

@ExtendWith(MockitoExtension.class)
class RecommendationEventWeightServiceImplTest {

    @Mock private RecommendationEventWeightRepository repository;

    private RecommendationEventWeightServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new RecommendationEventWeightServiceImpl(
                        repository, new RecommendationProperties());
    }

    @Test
    void refresh_loadsWeightsAndFlagsFromRepository() {
        when(repository.findAll()).thenReturn(seedRows());

        service.refresh();

        assertThat(service.weight(RecommendationEventType.post_like)).isEqualByComparingTo("3.0");
        assertThat(service.weight(RecommendationEventType.post_unlike))
                .isEqualByComparingTo("-3.0");
        // An unseeded event type carries no signal; zero spares callers a null check.
        assertThat(service.weight(RecommendationEventType.search)).isEqualByComparingTo("0");
        // The flag views are HashSet-backed, so ordered assertions would be flaky.
        assertThat(service.cfEnabled())
                .containsExactlyInAnyOrder(
                        RecommendationEventType.post_like,
                        RecommendationEventType.post_save,
                        RecommendationEventType.post_unlike);
        assertThat(service.trendingEnabled())
                .containsExactlyInAnyOrder(RecommendationEventType.post_like);
        assertThat(service.affinityEnabled())
                .containsExactlyInAnyOrder(
                        RecommendationEventType.post_like, RecommendationEventType.profile_follow);
    }

    @Test
    void weight_cachesAcrossCallsWithoutExtraReads() {
        when(repository.findAll()).thenReturn(seedRows());

        service.refresh();
        service.weight(RecommendationEventType.post_like);
        service.weight(RecommendationEventType.post_save);
        service.cfEnabled();

        // findAll is called only by the explicit refresh, not by subsequent reads.
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(1)).findAll();
    }

    @Test
    void refresh_failureKeepsPreviousSnapshot() {
        when(repository.findAll())
                .thenReturn(seedRows())
                .thenThrow(new RuntimeException("db blip"));

        service.refresh();
        BigDecimal weightBefore = service.weight(RecommendationEventType.post_like);

        service.refresh();

        assertThat(weightBefore).isEqualByComparingTo("3.0");
        assertThat(service.weight(RecommendationEventType.post_like)).isEqualByComparingTo("3.0");
    }

    private List<RecommendationEventWeight> seedRows() {
        return List.of(
                weight(RecommendationEventType.post_like, "3.0", true, true, true),
                weight(RecommendationEventType.post_save, "4.0", true, false, false),
                weight(RecommendationEventType.post_unlike, "-3.0", true, false, false),
                weight(RecommendationEventType.profile_follow, "0.0", false, false, true));
    }

    private RecommendationEventWeight weight(
            RecommendationEventType type,
            String weight,
            boolean cf,
            boolean trending,
            boolean affinity) {
        return new RecommendationEventWeight(
                type,
                new BigDecimal(weight),
                cf,
                trending,
                affinity,
                java.time.OffsetDateTime.now());
    }
}
