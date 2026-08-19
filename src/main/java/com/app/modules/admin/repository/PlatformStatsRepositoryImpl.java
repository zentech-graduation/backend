package com.app.modules.admin.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.app.modules.admin.enums.PlatformMetric;
import com.app.modules.admin.enums.StatGranularity;

@Repository
public class PlatformStatsRepositoryImpl implements PlatformStatsRepository {

    private static final String UPSERT_TEMPLATE =
            "INSERT INTO platform_stats"
                    + " (bucket_start, granularity, metric_key, dimension, value, computed_at)"
                    + " SELECT ?, CAST(? AS stat_granularity), ?, source.dimension, source.value,"
                    + " NOW() FROM (%s) AS source"
                    + " ON CONFLICT (bucket_start, granularity, metric_key, dimension)"
                    + " DO UPDATE SET value = EXCLUDED.value, computed_at = EXCLUDED.computed_at";

    private final JdbcTemplate jdbcTemplate;

    public PlatformStatsRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int collect(
            PlatformMetric metric,
            StatGranularity granularity,
            OffsetDateTime bucketStart,
            OffsetDateTime bucketEnd) {
        String sql = String.format(UPSERT_TEMPLATE, metric.selectSql());
        List<Object> args = new ArrayList<>();
        args.add(bucketStart);
        args.add(granularity.toJson());
        args.add(metric.key());
        args.addAll(bindsFor(metric, bucketStart, bucketEnd));
        return jdbcTemplate.update(sql, args.toArray());
    }

    // A gauge is bounded only by the end of the bucket, so every placeholder in its statement takes
    // the bucket end. A flow is bounded by both edges and always writes them in that order.
    private static List<Object> bindsFor(
            PlatformMetric metric, OffsetDateTime bucketStart, OffsetDateTime bucketEnd) {
        int placeholders = countPlaceholders(metric.selectSql());
        if (metric.kind() == PlatformMetric.Kind.FLOW) {
            List<Object> binds = new ArrayList<>();
            for (int i = 0; i < placeholders; i += 2) {
                binds.add(bucketStart);
                binds.add(bucketEnd);
            }
            return binds;
        }
        return Collections.nCopies(placeholders, bucketEnd);
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    // date_trunc on a timestamptz truncates in the session timezone, so leaving it implicit would
    // make a "day" start at local midnight and two deployments in different zones would bucket the
    // same rows into different days. Converting to UTC, truncating, and converting back pins the
    // boundary to UTC everywhere.
    private static final String UTC_DAY =
            "(date_trunc('day', bucket_start AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')";

    @Override
    public int rollUpDay(OffsetDateTime dayStart, OffsetDateTime dayEnd, StatGranularity fine) {
        // The CASE is the whole of the roll-up. A flow sums across the day's buckets; a gauge takes
        // the value of the last bucket, which is the end-of-day state. Summing 48 snapshots of
        // "total users" produces a number 48 times too large and looks plausible enough to ship.
        String sql =
                "INSERT INTO platform_stats"
                        + " (bucket_start, granularity, metric_key, dimension, value, computed_at)"
                        + " SELECT "
                        + UTC_DAY
                        + ", CAST('day' AS stat_granularity), metric_key, dimension,"
                        + " CASE WHEN metric_key IN ("
                        + placeholders(PlatformMetric.flowKeys().size())
                        + ") THEN sum(value)"
                        + " ELSE (array_agg(value ORDER BY bucket_start DESC))[1] END,"
                        + " NOW() FROM platform_stats"
                        + " WHERE granularity = CAST(? AS stat_granularity)"
                        + " AND bucket_start >= ? AND bucket_start < ?"
                        + " GROUP BY 1, metric_key, dimension"
                        + " ON CONFLICT (bucket_start, granularity, metric_key, dimension)"
                        + " DO UPDATE SET value = EXCLUDED.value,"
                        + " computed_at = EXCLUDED.computed_at";
        List<Object> args = new ArrayList<>(PlatformMetric.flowKeys());
        args.add(fine.toJson());
        args.add(dayStart);
        args.add(dayEnd);
        return jdbcTemplate.update(sql, args.toArray());
    }

    @Override
    public int deleteFineBuckets(
            OffsetDateTime dayStart, OffsetDateTime dayEnd, StatGranularity fine) {
        return jdbcTemplate.update(
                "DELETE FROM platform_stats WHERE granularity = CAST(? AS stat_granularity)"
                        + " AND bucket_start >= ? AND bucket_start < ?",
                fine.toJson(),
                dayStart,
                dayEnd);
    }

    @Override
    public List<OffsetDateTime> findDaysWithFineBucketsBefore(
            StatGranularity fine, OffsetDateTime olderThan) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT "
                        + UTC_DAY
                        + " AS day FROM platform_stats"
                        + " WHERE granularity = CAST(? AS stat_granularity) AND bucket_start < ?"
                        + " ORDER BY day",
                OffsetDateTime.class,
                fine.toJson(),
                olderThan);
    }

    @Override
    public int deleteDailyRowsBefore(OffsetDateTime olderThan) {
        return jdbcTemplate.update(
                "DELETE FROM platform_stats WHERE granularity = CAST('day' AS stat_granularity)"
                        + " AND bucket_start < ?",
                olderThan);
    }

    @Override
    public Optional<OffsetDateTime> findNewestBucket(StatGranularity granularity) {
        List<OffsetDateTime> newest =
                jdbcTemplate.queryForList(
                        "SELECT max(bucket_start) FROM platform_stats"
                                + " WHERE granularity = CAST(? AS stat_granularity)",
                        OffsetDateTime.class,
                        granularity.toJson());
        return newest.isEmpty() ? Optional.empty() : Optional.ofNullable(newest.get(0));
    }

    @Override
    public List<StatRow> findBucket(StatGranularity granularity, OffsetDateTime bucketStart) {
        return jdbcTemplate.query(
                "SELECT bucket_start, metric_key, dimension, value, computed_at"
                        + " FROM platform_stats"
                        + " WHERE granularity = CAST(? AS stat_granularity) AND bucket_start = ?",
                (rs, rowNum) ->
                        new StatRow(
                                rs.getObject("bucket_start", OffsetDateTime.class),
                                rs.getString("metric_key"),
                                rs.getString("dimension"),
                                rs.getLong("value"),
                                rs.getObject("computed_at", OffsetDateTime.class)),
                granularity.toJson(),
                bucketStart);
    }

    @Override
    public List<StatRow> findSeries(
            String metricKey, StatGranularity granularity, OffsetDateTime from, OffsetDateTime to) {
        return jdbcTemplate.query(
                "SELECT bucket_start, metric_key, dimension, value, computed_at"
                        + " FROM platform_stats"
                        + " WHERE metric_key = ? AND granularity = CAST(? AS stat_granularity)"
                        + " AND bucket_start >= ? AND bucket_start < ?"
                        + " ORDER BY bucket_start, dimension",
                (rs, rowNum) ->
                        new StatRow(
                                rs.getObject("bucket_start", OffsetDateTime.class),
                                rs.getString("metric_key"),
                                rs.getString("dimension"),
                                rs.getLong("value"),
                                rs.getObject("computed_at", OffsetDateTime.class)),
                metricKey,
                granularity.toJson(),
                from,
                to);
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
