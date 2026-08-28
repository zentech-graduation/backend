package com.app.modules.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RecommendationPropertiesTest {

    @Test
    void defaults_areNinetyDaysAndTwoThousandRowsAndThreeX() {
        RecommendationProperties properties = new RecommendationProperties();

        assertThat(properties.getReadSetWindow()).isEqualTo(Duration.ofDays(90));
        assertThat(properties.getReadSetMaxRows()).isEqualTo(2000);
        assertThat(properties.getTopUpOverfetchMultiplier()).isEqualTo(3);
    }
}
