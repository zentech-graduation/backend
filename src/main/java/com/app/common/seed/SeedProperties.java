package com.app.common.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds dev-database seed pipeline configuration from {@code app.seed.*}.
 *
 * <p>Whether the pipeline runs at all is controlled entirely by {@code SeedRunner}'s own
 * {@code @ConditionalOnProperty(name = "SEED_DATA")} - a second, independently bound "enabled" flag
 * here would either drift from that gate or duplicate it for no reason, so this record carries only
 * the one setting {@code SeedRunner} cannot express as a bean condition: {@code
 * requireLocalDatasource}, a safety guard a running instance can read and act on. A seed run that
 * cannot prove its datasource points at localhost must refuse to run, because {@code
 * SeedResetService.reset()} truncates every domain table and must never be allowed to run against a
 * shared or production database. Bound from {@code app.seed.require-local-datasource}, defaulting
 * to {@code true} via {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean requireLocalDatasource) {}
