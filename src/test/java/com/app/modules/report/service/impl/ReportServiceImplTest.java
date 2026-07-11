package com.app.modules.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.report.dto.request.CreateReportRequest;
import com.app.modules.report.dto.request.UpdateReportStatusRequest;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.mapper.ReportMapper;
import com.app.modules.report.repository.ReportRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {
    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new ReportServiceImpl(
                        reportRepository, userRepository, new ReportMapper(), entityManager);
    }

    @Test
    void submit_validTarget_persistsPendingReport() {
        UUID reporterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        User reporter = User.builder().id(reporterId).build();
        when(userRepository.findByIdAndDeletedAtIsNull(reporterId))
                .thenReturn(Optional.of(reporter));
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.setParameter("id", postId)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);
        when(reportRepository.save(any(Report.class)))
                .thenAnswer(
                        invocation -> {
                            Report report = invocation.getArgument(0);
                            report.setId(UUID.randomUUID());
                            return report;
                        });

        var response =
                service.submit(
                        reporterId,
                        new CreateReportRequest(
                                ReportType.POST, ReportReason.SPAM, postId, "Repeated ads"));

        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.entityId()).isEqualTo(postId);
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void submit_duplicateActiveReport_throwsConflict() {
        UUID reporterId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(reporterId))
                .thenReturn(Optional.of(User.builder().id(reporterId).build()));
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.setParameter("id", postId)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);
        when(reportRepository.existsByReporterIdAndReportTypeAndEntityIdAndStatusIn(
                        any(), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        reporterId,
                                        new CreateReportRequest(
                                                ReportType.POST, ReportReason.SPAM, postId, null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_DUPLICATE);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void transition_terminalReport_throwsInvalidTransition() {
        UUID reportId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Report report = Report.builder().id(reportId).status(ReportStatus.RESOLVED).build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(userRepository.findByIdAndDeletedAtIsNull(reviewerId))
                .thenReturn(Optional.of(User.builder().id(reviewerId).build()));

        assertThatThrownBy(
                        () ->
                                service.transitionStatus(
                                        reportId,
                                        reviewerId,
                                        new UpdateReportStatusRequest(
                                                ReportStatus.DISMISSED, "No violation")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_INVALID_TRANSITION);
    }
}
