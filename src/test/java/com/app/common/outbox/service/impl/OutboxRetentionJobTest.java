package com.app.common.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

import com.app.common.outbox.config.OutboxRetentionProperties;
import com.app.common.outbox.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxRetentionJobTest {

    @Mock private OutboxEventRepository repository;

    private OutboxRetentionProperties properties;
    private OutboxRetentionJob job;

    @BeforeEach
    void setUp() {
        properties = new OutboxRetentionProperties();
        properties.setEnabled(true);
        properties.setPublishedRetention(Duration.ofDays(30));
        properties.setBatchSize(100);
        properties.setMaxRowsPerRun(1_000);
        job = new OutboxRetentionJob(repository, properties);
    }

    @Test
    void purge_disabled_doesNotTouchRepository() {
        properties.setEnabled(false);

        job.purge();

        verifyNoInteractions(repository);
    }

    @Test
    void purge_shortBatch_stopsAfterFirstStatement() {
        when(repository.deletePublishedBefore(any(), eq(100))).thenReturn(37);

        job.purge();

        verify(repository, times(1)).deletePublishedBefore(any(), anyInt());
    }

    @Test
    void purge_emptyTable_stopsImmediately() {
        when(repository.deletePublishedBefore(any(), eq(100))).thenReturn(0);

        job.purge();

        verify(repository, times(1)).deletePublishedBefore(any(), anyInt());
    }

    @Test
    void purge_fullBatches_loopsUntilShortBatch() {
        when(repository.deletePublishedBefore(any(), eq(100)))
                .thenReturn(100)
                .thenReturn(100)
                .thenReturn(12);

        job.purge();

        verify(repository, times(3)).deletePublishedBefore(any(), anyInt());
    }

    @Test
    void purge_alwaysFullBatches_terminatesAtPerRunCeiling() {
        properties.setBatchSize(100);
        properties.setMaxRowsPerRun(500);
        when(repository.deletePublishedBefore(any(), anyInt())).thenReturn(100);

        job.purge();

        // The ceiling is the only thing that can stop this loop, since every batch comes back full.
        verify(repository, times(5)).deletePublishedBefore(any(), anyInt());
    }

    @Test
    void purge_ceilingNotAMultipleOfBatchSize_lastBatchIsTruncatedToTheRemainder() {
        properties.setBatchSize(100);
        properties.setMaxRowsPerRun(250);
        when(repository.deletePublishedBefore(any(), anyInt())).thenReturn(100).thenReturn(100);

        job.purge();

        ArgumentCaptor<Integer> limits = ArgumentCaptor.forClass(Integer.class);
        verify(repository, times(3)).deletePublishedBefore(any(), limits.capture());
        assertThat(limits.getAllValues()).containsExactly(100, 100, 50);
    }

    @Test
    void purge_cutoffIsRetentionWindowBeforeNow() {
        properties.setPublishedRetention(Duration.ofDays(7));
        when(repository.deletePublishedBefore(any(), anyInt())).thenReturn(0);
        OffsetDateTime before = OffsetDateTime.now().minusDays(7);

        job.purge();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deletePublishedBefore(cutoff.capture(), anyInt());
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(OffsetDateTime.now().minusDays(7).plusMinutes(1));
    }

    @Test
    void purge_nonPositiveBatchSize_fallsBackToDefaultRatherThanLoopingForever() {
        properties.setBatchSize(0);
        when(repository.deletePublishedBefore(any(), eq(500))).thenReturn(0);

        job.purge();

        verify(repository).deletePublishedBefore(any(), eq(500));
        verify(repository, never()).deletePublishedBefore(any(), eq(0));
    }
}
