package com.app.modules.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the {@code RefreshTokenPurgeJob}. Bound from {@code
 * app.auth.refresh-token-purge.*}.
 *
 * <ul>
 *   <li>{@code expired-grace}: rows with {@code expires_at < now - grace} are deleted. Allows a
 *       small margin for clock-skew or late revocation during a rolling restart.
 *   <li>{@code revoked-retention}: rows with {@code revoked_at < now - retention} are deleted.
 *       Keeps recent revocations around in case of audit/debugging.
 *   <li>{@code initial-delay}: delay before the first purge run after startup.
 *   <li>{@code fixed-delay}: delay between the end of one run and the start of the next.
 * </ul>
 */
@ConfigurationProperties(prefix = "app.auth.refresh-token-purge")
public record RefreshTokenPurgeProperties(
        @DefaultValue("PT1H") Duration expiredGrace,
        @DefaultValue("P7D") Duration revokedRetention,
        @DefaultValue("PT5M") Duration initialDelay,
        @DefaultValue("PT6H") Duration fixedDelay) {}
