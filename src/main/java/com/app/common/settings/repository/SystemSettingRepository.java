package com.app.common.settings.repository;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-only access to global runtime settings stored in PostgreSQL. */
@Repository
public class SystemSettingRepository {

    private final JdbcClient jdbcClient;

    public SystemSettingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> findValueByKey(String key) {
        return jdbcClient
                .sql("SELECT value FROM system_settings WHERE key = :key")
                .param("key", key)
                .query(String.class)
                .optional();
    }
}
