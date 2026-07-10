package com.app.modules.recommendation.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.modules.recommendation.config.RecommendationProperties;

/**
 * Daily job that pre-creates the next monthly partitions for {@code user_events} and
 * {@code impressions} and drops impressions partitions past the retention window.
 *
 * <p>DDL runs via {@link JdbcTemplate} because declarative partitioning has no JPA equivalent and
 * the precedent ({@code HashtagTrendingServiceImpl}) uses the same approach. Pre-creation is
 * idempotent ({@code CREATE TABLE IF NOT EXISTS}); drop only touches whole monthly partitions older
 * than {@code app.recommendation.partition.impressions-retention-months}, so no per-row deletes run.
 */
@Component
public class RecommendationPartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(RecommendationPartitionMaintenanceJob.class);

    private static final String USER_EVENTS = "user_events";
    private static final String IMPRESSIONS = "impressions";

    private final JdbcTemplate jdbcTemplate;
    private final RecommendationProperties properties;

    public RecommendationPartitionMaintenanceJob(
            JdbcTemplate jdbcTemplate, RecommendationProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${app.recommendation.partition.initial-delay:PT1M}",
            fixedDelayString = "${app.recommendation.partition.fixed-delay:PT24H}")
    public void maintain() {
        try {
            int created = preCreatePartitions();
            int dropped = dropExpiredImpressionPartitions();
            if (created > 0 || dropped > 0) {
                log.info(
                        "RecommendationPartitionMaintenanceJob: created {} partitions, dropped {}"
                                + " expired impressions partitions",
                        created,
                        dropped);
            }
        } catch (RuntimeException ex) {
            // A transient DB blip must not crash the scheduler; the next daily run retries.
            log.warn("RecommendationPartitionMaintenanceJob failed: {}", ex.getMessage());
        }
    }

    private int preCreatePartitions() {
        int created = 0;
        for (YearMonth month : upcomingMonths(properties.getPartition().getLookaheadMonths())) {
            created += createIfAbsent(USER_EVENTS, month);
            created += createIfAbsent(IMPRESSIONS, month);
        }
        return created;
    }

    private int dropExpiredImpressionPartitions() {
        YearMonth cutoff =
                YearMonth.now().minusMonths(properties.getPartition().getImpressionsRetentionMonths());
        int dropped = 0;
        for (YearMonth month : existingImpressionPartitionsBefore(cutoff)) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS impressions_" + suffix(month));
            dropped++;
        }
        return dropped;
    }

    private int createIfAbsent(String table, YearMonth month) {
        String partitionName = table + "_" + suffix(month);
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        // IF NOT EXISTS makes this idempotent across restarts and concurrent runs.
        String sql =
                "CREATE TABLE IF NOT EXISTS "
                        + partitionName
                        + " PARTITION OF "
                        + table
                        + " FOR VALUES FROM ('"
                        + start
                        + "') TO ('"
                        + end
                        + "')";
        jdbcTemplate.execute(sql);
        return 1;
    }

    private List<YearMonth> upcomingMonths(int count) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 0; i < count; i++) {
            months.add(current.plusMonths(i));
        }
        return months;
    }

    private List<YearMonth> existingImpressionPartitionsBefore(YearMonth cutoff) {
        // pg_inherits lists child partitions of the impressions parent table; only monthly-named
        // partitions (impressions_YYYY_MM) are candidates for retention drop.
        List<String> names =
                jdbcTemplate.queryForList(
                        """
						SELECT c.relname
						FROM pg_inherits i
						JOIN pg_class c ON c.oid = i.inhrelid
						JOIN pg_class p ON p.oid = i.inhparent
						JOIN pg_namespace n ON n.oid = p.relnamespace
						WHERE n.nspname = 'public'
						  AND p.relname = 'impressions'
						  AND c.relname LIKE 'impressions\\\\_\\\\____\\\\_\\\\____'
						""",
                        String.class);
        List<YearMonth> result = new ArrayList<>();
        for (String name : names) {
            parseSuffix(name)
                    .ifPresent(
                            month -> {
                                if (month.isBefore(cutoff)) {
                                    result.add(month);
                                }
                            });
        }
        return result;
    }

    private static java.util.Optional<YearMonth> parseSuffix(String partitionName) {
        String suffix = partitionName.substring("impressions_".length());
        try {
            return java.util.Optional.of(YearMonth.parse(suffix));
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private static String suffix(YearMonth month) {
        return month.getYear() + "_" + String.format("%02d", month.getMonthValue());
    }
}
