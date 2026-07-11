package com.app.modules.recommendation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.app.modules.recommendation.config.RecommendationProperties;

@ExtendWith(MockitoExtension.class)
class RecommendationPartitionMaintenanceJobTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private RecommendationProperties properties;
    private RecommendationPartitionMaintenanceJob job;

    @BeforeEach
    void setUp() {
        properties = new RecommendationProperties();
        properties.getPartition().setLookaheadMonths(2);
        properties.getPartition().setImpressionsRetentionMonths(3);
        job = new RecommendationPartitionMaintenanceJob(jdbcTemplate, properties);
        // Not every test walks both the create and the drop paths; lenient keeps strict-stubs
        // happy for the paths a given test does not exercise.
        lenient()
                .when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of());
    }

    @Test
    void maintain_missingPartitions_createsLookaheadMonths() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(null);
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        YearMonth next = current.plusMonths(1);

        job.maintain();

        verify(jdbcTemplate).execute(createSql("user_events", current));
        verify(jdbcTemplate).execute(createSql("impressions", current));
        verify(jdbcTemplate).execute(createSql("user_events", next));
        verify(jdbcTemplate).execute(createSql("impressions", next));
    }

    @Test
    void maintain_partitionsAlreadyExist_skipsCreation() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("public.some_partition");

        job.maintain();

        verify(jdbcTemplate, never()).execute(contains("CREATE TABLE"));
    }

    @Test
    void maintain_partitionPastRetention_dropsIt() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("public.some_partition");
        String currentSuffix =
                YearMonth.now(ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ofPattern("uuuu_MM"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("impressions_2020_01", "impressions_" + currentSuffix));

        job.maintain();

        verify(jdbcTemplate).execute("DROP TABLE IF EXISTS impressions_2020_01");
        verify(jdbcTemplate, never()).execute("DROP TABLE IF EXISTS impressions_" + currentSuffix);
    }

    @Test
    void maintain_databaseFailure_doesNotPropagate() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.maintain()).doesNotThrowAnyException();
    }

    @Test
    void parseSuffix_monthlyPartitionName_parsesUnderscoreFormat() {
        assertThat(RecommendationPartitionMaintenanceJob.parseSuffix("impressions_2026_07"))
                .contains(YearMonth.of(2026, 7));
    }

    @Test
    void parseSuffix_nonMonthlyName_returnsEmpty() {
        assertThat(RecommendationPartitionMaintenanceJob.parseSuffix("impressions_default"))
                .isEmpty();
    }

    private static String createSql(String table, YearMonth month) {
        String suffix = month.format(java.time.format.DateTimeFormatter.ofPattern("uuuu_MM"));
        return "CREATE TABLE IF NOT EXISTS "
                + table
                + "_"
                + suffix
                + " PARTITION OF "
                + table
                + " FOR VALUES FROM ('"
                + month.atDay(1)
                + "') TO ('"
                + month.plusMonths(1).atDay(1)
                + "')";
    }
}
