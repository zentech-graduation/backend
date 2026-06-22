package com.app.modules.auth.service.impl;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.auth.config.RefreshTokenPurgeProperties;
import com.app.modules.auth.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically deletes {@code refresh_tokens} rows that are either expired past the configured
 * grace period or revoked past the configured retention window.
 *
 * <p>Without this job the table grows monotonically — every login/rotation inserts a row while
 * revocation only sets {@code revoked_at} — eventually bloating the {@code token_hash} UNIQUE index
 * and degrading {@code rotate()} lookups.
 */
@Slf4j
@Component
public class RefreshTokenPurgeJob {

    private final RefreshTokenRepository repository;
    private final RefreshTokenPurgeProperties properties;

    public RefreshTokenPurgeJob(
            RefreshTokenRepository repository, RefreshTokenPurgeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${app.auth.refresh-token-purge.initial-delay:PT5M}",
            fixedDelayString = "${app.auth.refresh-token-purge.fixed-delay:PT6H}")
    @Transactional
    public void purge() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiredBefore = now.minus(properties.expiredGrace());
        OffsetDateTime revokedBefore = now.minus(properties.revokedRetention());

        int deleted = repository.deleteExpiredAndRevoked(expiredBefore, revokedBefore);
        if (deleted > 0) {
            log.info(
                    "RefreshTokenPurgeJob: deleted {} stale rows "
                            + "(expiredBefore={}, revokedBefore={})",
                    deleted,
                    expiredBefore,
                    revokedBefore);
        }
    }
}
