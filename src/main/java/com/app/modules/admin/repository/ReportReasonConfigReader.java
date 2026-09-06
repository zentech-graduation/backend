package com.app.modules.admin.repository;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The first runtime reader of {@code report_reason_configs}.
 *
 * <p>The table has existed since V18 as the metadata layer for the {@code report_reason} enum, but
 * nothing read it, so its {@code is_enabled} column decided nothing. Warning reasons are drawn from
 * it, which is exactly the split {@code GLOBAL_RULES.md} describes: the enum constrains what may be
 * stored, the config table decides what is currently offered. Retiring a reason is then a row
 * update rather than a deploy.
 *
 * <p>Native, with no entity behind it, because the read is one boolean and the table is
 * configuration rather than a domain aggregate.
 */
@Component
public class ReportReasonConfigReader {

    private final JdbcClient jdbcClient;

    public ReportReasonConfigReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Reports whether a reason key exists and whether it is currently enabled.
     *
     * @param reasonKey key to look up
     * @return true when the row exists and is enabled, false when it exists and is disabled, empty
     *     when no row holds that key
     */
    public Optional<Boolean> findEnabledByReasonKey(String reasonKey) {
        return jdbcClient
                .sql("SELECT is_enabled FROM report_reason_configs WHERE reason_key = :key")
                .param("key", reasonKey)
                .query(Boolean.class)
                .optional();
    }
}
