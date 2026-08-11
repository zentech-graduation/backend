package com.app.modules.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.report.enums.ReportType;
import com.app.modules.report.repository.ReportRepository;

@ExtendWith(MockitoExtension.class)
class ReportedTargetServiceImplTest {

    @Mock private ReportRepository reportRepository;

    private ReportedTargetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportedTargetServiceImpl(reportRepository);
    }

    @Test
    void loadReportedEntityIds_emptyIdSet_returnsEmptyWithoutQuerying() {
        Set<UUID> result =
                service.loadReportedEntityIds(UUID.randomUUID(), ReportType.POST, List.of());

        assertThat(result).isEmpty();
        verify(reportRepository, never()).findReportedEntityIds(any(), any(), anyCollection());
    }

    @Test
    void loadReportedEntityIds_nullViewer_returnsEmptyWithoutQuerying() {
        Set<UUID> result =
                service.loadReportedEntityIds(null, ReportType.POST, List.of(UUID.randomUUID()));

        assertThat(result).isEmpty();
        verify(reportRepository, never()).findReportedEntityIds(any(), any(), anyCollection());
    }

    @Test
    void loadReportedEntityIds_nullIdCollection_returnsEmptyWithoutQuerying() {
        Set<UUID> result = service.loadReportedEntityIds(UUID.randomUUID(), ReportType.POST, null);

        assertThat(result).isEmpty();
        verify(reportRepository, never()).findReportedEntityIds(any(), any(), anyCollection());
    }

    @Test
    void loadReportedEntityIds_returnsOnlyTheReportedSubset() {
        UUID viewer = UUID.randomUUID();
        UUID reported = UUID.randomUUID();
        UUID notReported = UUID.randomUUID();
        when(reportRepository.findReportedEntityIds(
                        viewer, ReportType.POST, Set.of(reported, notReported)))
                .thenReturn(List.of(reported));

        Set<UUID> result =
                service.loadReportedEntityIds(
                        viewer, ReportType.POST, List.of(reported, notReported));

        assertThat(result).containsExactly(reported);
    }

    @Test
    void loadReportedEntityIds_duplicateIdsInInput_areDeduplicatedBeforeQuerying() {
        UUID viewer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(reportRepository.findReportedEntityIds(viewer, ReportType.COMMENT, Set.of(target)))
                .thenReturn(List.of(target));

        Set<UUID> result =
                service.loadReportedEntityIds(
                        viewer, ReportType.COMMENT, List.of(target, target, target));

        assertThat(result).containsExactly(target);
    }
}
