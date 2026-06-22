package com.app.modules.auth.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.auth.config.RefreshTokenPurgeProperties;
import com.app.modules.auth.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPurgeJobTest {

    @Mock private RefreshTokenRepository repository;

    private RefreshTokenPurgeProperties properties;
    private RefreshTokenPurgeJob job;

    @BeforeEach
    void setUp() {
        properties =
                new RefreshTokenPurgeProperties(
                        Duration.ofHours(1),
                        Duration.ofDays(7),
                        Duration.ofMinutes(5),
                        Duration.ofHours(6));
        job = new RefreshTokenPurgeJob(repository, properties);
    }

    @Test
    void purge_deletesStaleTokensBasedOnConfiguredGraceAndRetention() {
        when(repository.deleteExpiredAndRevoked(
                        any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(5);

        job.purge();

        verify(repository)
                .deleteExpiredAndRevoked(any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    void purge_noRowsDeleted_runsWithoutLoggingStaleRows() {
        when(repository.deleteExpiredAndRevoked(
                        any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(0);

        job.purge();

        verify(repository)
                .deleteExpiredAndRevoked(any(OffsetDateTime.class), any(OffsetDateTime.class));
    }
}
