package com.app.modules.admin.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the scheduled suspension-reinstatement sweep. Bound from {@code
 * app.admin.suspension-expiry.*}.
 *
 * <ul>
 *   <li>{@code enabled}: whether the sweep bean is registered at all. Defaults to true.
 *   <li>{@code batch-size}: maximum accounts reinstated per pass, so one pass cannot hold a
 *       connection over an unbounded backlog.
 *   <li>{@code initial-delay}: delay before the first pass after startup.
 *   <li>{@code fixed-delay}: delay between the end of one pass and the start of the next. The
 *       authentication path repairs a lapsed suspension on first use regardless, so this interval
 *       bounds only how long an account nobody logs into stays suspended in the table.
 * </ul>
 */
@ConfigurationProperties(prefix = "app.admin.suspension-expiry")
public record SuspensionExpiryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500") int batchSize,
        @DefaultValue("PT2M") Duration initialDelay,
        @DefaultValue("PT15M") Duration fixedDelay) {}
