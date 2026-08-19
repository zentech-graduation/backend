package com.app.modules.admin.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for platform statistics collection and roll-up. Bound from {@code app.stats.*}.
 *
 * <ul>
 *   <li>{@code enabled}: whether the two scheduled jobs are registered at all. Defaults to true.
 *   <li>{@code interval}: bucket width for the fine-grained series, and the delay between
 *       collection passes. Buckets are aligned to the epoch, not to process start.
 *   <li>{@code fine-retention}: how long fine-grained buckets are kept before the daily job rolls
 *       them up into one row per metric per day and deletes them.
 *   <li>{@code daily-retention}: how long rolled-up daily rows are kept before deletion. There is
 *       no backfill, so a row deleted here is gone.
 *   <li>{@code rollup-cron}: when the daily roll-up and retention pass runs, in UTC.
 * </ul>
 *
 * <p>A single application instance is assumed. No distributed scheduler lock exists in this
 * codebase, so two instances would each run both jobs. The composite primary key on {@code
 * platform_stats} together with {@code ON CONFLICT DO UPDATE} keeps that harmless rather than
 * duplicative, but it is an assumption to revisit before scaling out.
 */
@ConfigurationProperties(prefix = "app.stats")
public record StatsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT30M") Duration interval,
        @DefaultValue("P30D") Duration fineRetention,
        @DefaultValue("P365D") Duration dailyRetention,
        @DefaultValue("0 20 3 * * *") String rollupCron) {}
