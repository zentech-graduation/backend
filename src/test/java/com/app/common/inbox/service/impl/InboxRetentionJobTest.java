package com.app.common.inbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.inbox.config.InboxRetentionProperties;
import com.app.common.inbox.repository.ProcessedMessageRepository;

@ExtendWith(MockitoExtension.class)
class InboxRetentionJobTest {

    @Mock private ProcessedMessageRepository repository;

    private InboxRetentionProperties properties;
    private InboxRetentionJob job;

    @BeforeEach
    void setUp() {
        properties = new InboxRetentionProperties();
        properties.setEnabled(true);
        properties.setProcessedRetention(Duration.ofDays(30));
        properties.setBatchSize(100);
        properties.setMaxRowsPerRun(1_000);
        job = new InboxRetentionJob(repository, properties);
    }

    @Test
    void purge_disabled_doesNotTouchRepository() {
        properties.setEnabled(false);

        job.purge();

        verifyNoInteractions(repository);
    }

    @Test
    void purge_shortBatch_stopsAfterFirstStatement() {
        when(repository.deleteProcessedBefore(any(), eq(100))).thenReturn(5);

        job.purge();

        verify(repository, times(1)).deleteProcessedBefore(any(), anyInt());
    }

    @Test
    void purge_fullBatches_loopsUntilShortBatch() {
        when(repository.deleteProcessedBefore(any(), eq(100)))
                .thenReturn(100)
                .thenReturn(100)
                .thenReturn(0);

        job.purge();

        verify(repository, times(3)).deleteProcessedBefore(any(), anyInt());
    }

    @Test
    void purge_alwaysFullBatches_terminatesAtPerRunCeiling() {
        properties.setBatchSize(100);
        properties.setMaxRowsPerRun(300);
        when(repository.deleteProcessedBefore(any(), anyInt())).thenReturn(100);

        job.purge();

        verify(repository, times(3)).deleteProcessedBefore(any(), anyInt());
    }

    @Test
    void purge_defaultRetention_keepsThirtyDaysOfIdempotencyGuards() {
        properties.setProcessedRetention(null);
        when(repository.deleteProcessedBefore(any(), anyInt())).thenReturn(0);
        OffsetDateTime expected = OffsetDateTime.now().minusDays(30);

        job.purge();

        // A shorter window than the longest redelivery path would let a replayed message re-run its
        // side effects, so the fallback must not silently shrink to zero.
        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteProcessedBefore(cutoff.capture(), anyInt());
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(expected.minusMinutes(1))
                .isBeforeOrEqualTo(expected.plusMinutes(1));
    }
}
