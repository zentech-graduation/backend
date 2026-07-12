package com.app.modules.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.dto.response.ReportResponse;
import com.app.modules.report.dto.response.ReportSummaryResponse;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.mapper.ReportMapper;
import com.app.modules.report.repository.ReportRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ReportMapper reportMapper;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportRepository, reportMapper);
    }

    @Test
    void submitReport_validTarget_persistsPendingReport() {
        UUID reporterId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        CreateReportRequest request =
                new CreateReportRequest(
                        ReportType.POST, ReportReason.SPAM, entityId, "Repeated advertisements");
        ReportResponse expected = response(ReportStatus.PENDING);
        when(reportRepository.findOwnerId(ReportType.POST, entityId))
                .thenReturn(Optional.of(ownerId));
        when(reportRepository.save(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reportMapper.toResponse(any(Report.class))).thenReturn(expected);

        ReportResponse result = service.submitReport(reporterId, request);

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReporterId()).isEqualTo(reporterId);
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void submitReport_missingTarget_throwsTargetNotFound() {
        UUID entityId = UUID.randomUUID();
        when(reportRepository.findOwnerId(ReportType.USER, entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.submitReport(
                                        UUID.randomUUID(),
                                        new CreateReportRequest(
                                                ReportType.USER,
                                                ReportReason.HARASSMENT,
                                                entityId,
                                                null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_TARGET_NOT_FOUND);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void submitReport_selfOwnedTarget_throwsSelfNotAllowed() {
        UUID reporterId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(reportRepository.findOwnerId(ReportType.POST, entityId))
                .thenReturn(Optional.of(reporterId));

        assertThatThrownBy(
                        () ->
                                service.submitReport(
                                        reporterId,
                                        new CreateReportRequest(
                                                ReportType.POST,
                                                ReportReason.OTHER,
                                                entityId,
                                                null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_SELF_NOT_ALLOWED);
    }

    @Test
    void submitReport_duplicateTarget_throwsConflict() {
        UUID reporterId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(reportRepository.findOwnerId(ReportType.COMMENT, entityId))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(reportRepository.existsByReporterIdAndReportTypeAndEntityId(
                        reporterId, ReportType.COMMENT, entityId))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.submitReport(
                                        reporterId,
                                        new CreateReportRequest(
                                                ReportType.COMMENT,
                                                ReportReason.HATE_SPEECH,
                                                entityId,
                                                null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_DUPLICATE);
    }

    @Test
    void listReports_filteredCursor_mapsSummaryContent() {
        Report report = Report.builder().status(ReportStatus.PENDING).build();
        ReportSummaryResponse mapped = summary(ReportStatus.PENDING);
        when(reportRepository.findFirstReportsByStatusAndReportType(
                        ReportStatus.PENDING, ReportType.POST, 21))
                .thenReturn(List.of(report));
        when(reportMapper.toSummaryResponseList(List.of(report))).thenReturn(List.of(mapped));

        var result = service.listReports(ReportStatus.PENDING, ReportType.POST, null, 20);

        assertThat(result.getContent()).containsExactly(mapped);
        assertThat(result.getPageInfo().isHasNextPage()).isFalse();
    }

    @Test
    void getPendingReports_firstPage_queriesPendingQueueInFifoOrder() {
        Report report = Report.builder().status(ReportStatus.PENDING).build();
        ReportSummaryResponse mapped = summary(ReportStatus.PENDING);
        when(reportRepository.findFirstReportsByStatusOldestFirst(ReportStatus.PENDING, 21))
                .thenReturn(List.of(report));
        when(reportMapper.toSummaryResponseList(List.of(report))).thenReturn(List.of(mapped));

        var result = service.getPendingReports(null, 20);

        assertThat(result.getContent()).containsExactly(mapped);
        assertThat(result.getPageInfo().isHasPreviousPage()).isFalse();
    }

    @Test
    void getReport_missingReport_throwsNotFound() {
        UUID reportId = UUID.randomUUID();
        when(reportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReport(reportId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void updateStatus_pendingToReviewing_recordsReviewerMetadata() {
        UUID reportId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).status(ReportStatus.PENDING).build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);
        when(reportMapper.toResponse(report)).thenReturn(response(ReportStatus.REVIEWING));

        service.updateStatus(
                reportId, reviewerId, new UpdateReportStatusRequest(ReportStatus.REVIEWING, null));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REVIEWING);
        assertThat(report.getReviewedBy()).isEqualTo(reviewerId);
        assertThat(report.getReviewedAt()).isNotNull();
        assertThat(report.getResolutionNote()).isNull();
    }

    @Test
    void updateStatus_terminalWithoutNote_throwsResolutionNoteRequired() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).status(ReportStatus.REVIEWING).build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(
                        () ->
                                service.updateStatus(
                                        reportId,
                                        UUID.randomUUID(),
                                        new UpdateReportStatusRequest(ReportStatus.RESOLVED, " ")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_RESOLUTION_NOTE_REQUIRED);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void updateStatus_dismissedWithoutNote_throwsResolutionNoteRequired() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).status(ReportStatus.REVIEWING).build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(
                        () ->
                                service.updateStatus(
                                        reportId,
                                        UUID.randomUUID(),
                                        new UpdateReportStatusRequest(
                                                ReportStatus.DISMISSED, null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_RESOLUTION_NOTE_REQUIRED);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void updateStatus_terminalReport_throwsInvalidTransition() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).status(ReportStatus.RESOLVED).build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(
                        () ->
                                service.updateStatus(
                                        reportId,
                                        UUID.randomUUID(),
                                        new UpdateReportStatusRequest(
                                                ReportStatus.DISMISSED, "Not actionable")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_INVALID_TRANSITION);
    }

    private static ReportResponse response(ReportStatus status) {
        return new ReportResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportType.POST,
                ReportReason.SPAM,
                UUID.randomUUID(),
                null,
                status,
                null,
                null,
                null,
                null);
    }

    private static ReportSummaryResponse summary(ReportStatus status) {
        return new ReportSummaryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportType.POST,
                ReportReason.SPAM,
                UUID.randomUUID(),
                status,
                null);
    }
}
