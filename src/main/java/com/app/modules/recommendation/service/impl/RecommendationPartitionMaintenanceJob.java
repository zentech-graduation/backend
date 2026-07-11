package com.app.modules.recommendation.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.modules.recommendation.config.RecommendationProperties;

/**
 * Daily job that pre-creates the next monthly partitions for {@code user_events} and {@code
 * impressions} and drops impressions partitions past the retention window.
 *
 * <p>DDL runs via {@link JdbcTemplate} because declarative partitioning has no JPA equivalent and
 * the precedent ({@code HashtagTrendingServiceImpl}) uses the same approach. Pre-creation is
 * idempotent ({@code CREATE TABLE IF NOT EXISTS}); drop only touches whole monthly partitions older
 * than {@code app.recommendation.partition.impressions-retention-months}, so no per-row deletes
 * run.
 */
@Component
public class RecommendationPartitionMaintenanceJob {

    private static final Logger log =
            LoggerFactory.getLogger(RecommendationPartitionMaintenanceJob.class);

    private static final String USER_EVENTS = "user_events";
    private static final String IMPRESSIONS = "impressions";

    // Partition suffixes are formatted and parsed as e.g. "2026_07"; YearMonth.parse alone would
    // require the ISO "2026-07" form and reject every real partition name.
    private static final DateTimeFormatter SUFFIX_FORMAT = DateTimeFormatter.ofPattern("uuuu_MM");

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
        // All decay/retention math is anchored to UTC; YearMonth.now() would use the machine
        // timezone and drift the cutoff by a month around midnight boundaries.
        YearMonth cutoff =
                YearMonth.now(ZoneOffset.UTC)
                        .minusMonths(properties.getPartition().getImpressionsRetentionMonths());
        int dropped = 0;
        for (YearMonth month : existingImpressionPartitionsBefore(cutoff)) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS impressions_" + suffix(month));
            dropped++;
        }
        return dropped;
    }

    private int createIfAbsent(String table, YearMonth month) {
        String partitionName = table + "_" + suffix(month);
        if (partitionExists(partitionName)) {
            return 0;
        }
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

    private boolean partitionExists(String partitionName) {
        // to_regclass returns NULL when the relation does not exist, without throwing.
        String found =
                jdbcTemplate.queryForObject(
                        "SELECT to_regclass(?)::text", String.class, "public." + partitionName);
        return found != null;
    }

    private List<YearMonth> upcomingMonths(int count) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        for (int i = 0; i < count; i++) {
            months.add(current.plusMonths(i));
        }
        return months;
    }

    private List<YearMonth> existingImpressionPartitionsBefore(YearMonth cutoff) {
        // pg_inherits lists child partitions of the impressions parent table; the POSIX regex keeps
        // only monthly-named partitions (impressions_YYYY_MM), excluding impressions_default.
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
						AND c.relname ~ '^impressions_\\d{4}_\\d{2}$'
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

    static Optional<YearMonth> parseSuffix(String partitionName) {
        String suffix = partitionName.substring("impressions_".length());
        try {
            return Optional.of(YearMonth.parse(suffix, SUFFIX_FORMAT));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String suffix(YearMonth month) {
        return month.format(SUFFIX_FORMAT);
    }
}
