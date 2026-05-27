package com.app.common.settings.service;

/** Service API for reading global runtime settings from PostgreSQL. */
public interface SystemSettingService {

    /**
     * Reads a required system setting and parses it as a long value.
     *
     * @param key setting key stored in PostgreSQL
     * @return parsed long value
     */
    long getRequiredLong(String key);
}
