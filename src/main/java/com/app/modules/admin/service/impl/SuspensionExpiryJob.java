package com.app.modules.admin.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.modules.admin.config.SuspensionExpiryProperties;
import com.app.modules.admin.service.SuspensionExpiryService;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically returns accounts whose fixed-term suspension has lapsed to active.
 *
 * <p>The authentication path already repairs a lapsed suspension the first time the account is
 * used, so this sweep exists for the accounts nobody tries to use: without it a suspension that
 * expired months ago would still read as {@code suspended} in every administrative view and in
 * every report.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.admin.suspension-expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SuspensionExpiryJob {

    private final SuspensionExpiryService suspensionExpiryService;
    private final SuspensionExpiryProperties properties;

    public SuspensionExpiryJob(
            SuspensionExpiryService suspensionExpiryService,
            SuspensionExpiryProperties properties) {
        this.suspensionExpiryService = suspensionExpiryService;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${app.admin.suspension-expiry.initial-delay:PT2M}",
            fixedDelayString = "${app.admin.suspension-expiry.fixed-delay:PT15M}")
    public void reinstateExpiredSuspensions() {
        int reinstated = suspensionExpiryService.reinstateExpiredBatch(properties.batchSize());
        if (reinstated > 0) {
            log.info("SuspensionExpiryJob: returned {} accounts to active", reinstated);
        }
    }
}
