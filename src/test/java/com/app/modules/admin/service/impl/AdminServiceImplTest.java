package com.app.modules.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminActionSummaryResponse;
import com.app.modules.admin.entity.AdminAction;
import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.admin.mapper.AdminActionMapper;
import com.app.modules.admin.repository.AdminActionRepository;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock private AdminActionRepository adminActionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private AdminActionMapper adminActionMapper;

    private AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new AdminServiceImpl(
                        adminActionRepository,
                        userRepository,
                        postRepository,
                        commentRepository,
                        reportRepository,
                        adminActionMapper);
    }

    @Test
    void suspendUser_activeUser_updatesAndAudits() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).status(UserStatus.ACTIVE).build();
        AdminActionResponse expected = response(AdminActionType.SUSPEND_USER);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        stubAudit(expected);

        AdminActionResponse result =
                service.suspendUser(
                        actorId, userId, new AdminActionRequest("Policy breach", null, Map.of()));

        assertThat(result).isEqualTo(expected);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
        ArgumentCaptor<AdminAction> action = ArgumentCaptor.forClass(AdminAction.class);
        verify(adminActionRepository).insert(action.capture());
        assertThat(action.getValue().getAdminId()).isEqualTo(actorId);
        assertThat(action.getValue().getTargetUserId()).isEqualTo(userId);
    }

    @Test
    void unbanUser_activeUser_throwsInvalidTransition() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(User.builder().status(UserStatus.ACTIVE).build()));

        assertThatThrownBy(
                        () ->
                                service.unbanUser(
                                        UUID.randomUUID(),
                                        userId,
                                        new AdminActionRequest("Review", null, null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        verify(adminActionRepository, never()).insert(any());
    }

    @Test
    void removePost_livePost_softDeletesAndAudits() {
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AdminActionResponse expected = response(AdminActionType.REMOVE_POST);
        when(postRepository.findOwnerIdIncludingDeleted(postId)).thenReturn(Optional.of(ownerId));
        when(postRepository.findStatusIncludingDeleted(postId))
                .thenReturn(Optional.of("published"));
        stubAudit(expected);

        AdminActionResponse result =
                service.removePost(
                        UUID.randomUUID(), postId, new AdminActionRequest("Violation", null, null));

        assertThat(result).isEqualTo(expected);
        verify(postRepository)
                .applyAdminModeration(
                        org.mockito.ArgumentMatchers.eq(postId),
                        org.mockito.ArgumentMatchers.eq("removed"),
                        any(OffsetDateTime.class));
    }

    @Test
    void restoreComment_deletedComment_clearsDeletedAtAndAudits() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findOwnerIdIncludingDeleted(commentId))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(commentRepository.isDeletedIncludingDeleted(commentId)).thenReturn(Optional.of(true));
        AdminActionResponse expected = response(AdminActionType.RESTORE_COMMENT);
        stubAudit(expected);

        AdminActionResponse result =
                service.restoreComment(
                        UUID.randomUUID(),
                        commentId,
                        new AdminActionRequest("Appeal accepted", null, null));

        assertThat(result).isEqualTo(expected);
        verify(commentRepository).applyAdminModeration(commentId, null);
    }

    @Test
    void resolveReport_pendingReport_recordsReviewerAndAudit() {
        UUID actorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Report report =
                Report.builder()
                        .id(reportId)
                        .reportType(ReportType.POST)
                        .reportReason(ReportReason.SPAM)
                        .entityId(UUID.randomUUID())
                        .status(ReportStatus.PENDING)
                        .build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        AdminActionResponse expected = response(AdminActionType.RESOLVE_REPORT);
        stubAudit(expected);

        AdminActionResponse result =
                service.resolveReport(
                        actorId, reportId, new AdminActionRequest("Confirmed", null, null));

        assertThat(result).isEqualTo(expected);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getReviewedBy()).isEqualTo(actorId);
        assertThat(report.getResolutionNote()).isEqualTo("Confirmed");
        verify(reportRepository).save(report);
    }

    @Test
    void resolveReport_terminalReport_throwsInvalidTransition() {
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder().status(ReportStatus.DISMISSED).build();
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(
                        () ->
                                service.resolveReport(
                                        UUID.randomUUID(),
                                        reportId,
                                        new AdminActionRequest("Reopen not allowed", null, null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.REPORT_INVALID_TRANSITION);
    }

    @Test
    void getActions_firstPage_mapsContentAndProbeRow() {
        AdminAction first = action(AdminActionType.BAN_USER);
        AdminAction probe = action(AdminActionType.REMOVE_POST);
        AdminActionSummaryResponse mapped = summary(AdminActionType.BAN_USER);
        when(adminActionRepository.findActions(null, null, null, null, null, 2))
                .thenReturn(List.of(first, probe));
        when(adminActionMapper.toSummaryResponseList(List.of(first))).thenReturn(List.of(mapped));

        var result = service.getActions(null, null, null, 1);

        assertThat(result.getContent()).containsExactly(mapped);
        assertThat(result.getPageInfo().isHasNextPage()).isTrue();
        assertThat(result.getPageInfo().getEndCursor()).isNotBlank();
    }

    @Test
    void getActionById_missingAction_throwsNotFound() {
        UUID actionId = UUID.randomUUID();
        when(adminActionRepository.findById(actionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActionById(actionId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.ADMIN_ACTION_NOT_FOUND);
    }

    private void stubAudit(AdminActionResponse response) {
        when(adminActionRepository.insert(any(AdminAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminActionMapper.toResponse(any(AdminAction.class))).thenReturn(response);
    }

    private static AdminAction action(AdminActionType actionType) {
        return AdminAction.builder()
                .id(UUID.randomUUID())
                .actionType(actionType)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private static AdminActionResponse response(AdminActionType actionType) {
        return new AdminActionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                actionType,
                UUID.randomUUID(),
                "user",
                UUID.randomUUID(),
                null,
                "Reason",
                Map.of(),
                OffsetDateTime.now());
    }

    private static AdminActionSummaryResponse summary(AdminActionType actionType) {
        return new AdminActionSummaryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                actionType,
                UUID.randomUUID(),
                "user",
                UUID.randomUUID(),
                null,
                "Reason",
                OffsetDateTime.now());
    }
}
