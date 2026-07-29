package com.app.modules.admin.service.impl;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.Cursor;
import com.app.common.pagination.CursorCodec;
import com.app.common.pagination.TimeCursors;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminActionSummaryResponse;
import com.app.modules.admin.entity.AdminAction;
import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.admin.mapper.AdminActionMapper;
import com.app.modules.admin.repository.AdminActionRepository;
import com.app.modules.admin.service.AdminService;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminActionRepository adminActionRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final AdminActionMapper adminActionMapper;

    public AdminServiceImpl(
            AdminActionRepository adminActionRepository,
            UserRepository userRepository,
            PostRepository postRepository,
            CommentRepository commentRepository,
            ReportRepository reportRepository,
            AdminActionMapper adminActionMapper) {
        this.adminActionRepository = adminActionRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.adminActionMapper = adminActionMapper;
    }

    @Override
    @Transactional
    public AdminActionResponse banUser(UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(actorId, userId, AdminActionType.BAN_USER, request);
    }

    @Override
    @Transactional
    public AdminActionResponse unbanUser(UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(actorId, userId, AdminActionType.UNBAN_USER, request);
    }

    @Override
    @Transactional
    public AdminActionResponse suspendUser(UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(actorId, userId, AdminActionType.SUSPEND_USER, request);
    }

    @Override
    @Transactional
    public AdminActionResponse unsuspendUser(
            UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(actorId, userId, AdminActionType.UNSUSPEND_USER, request);
    }

    @Override
    @Transactional
    public AdminActionResponse removePost(UUID actorId, UUID postId, AdminActionRequest request) {
        return moderatePost(actorId, postId, AdminActionType.REMOVE_POST, request);
    }

    @Override
    @Transactional
    public AdminActionResponse restorePost(UUID actorId, UUID postId, AdminActionRequest request) {
        return moderatePost(actorId, postId, AdminActionType.RESTORE_POST, request);
    }

    @Override
    @Transactional
    public AdminActionResponse removeComment(
            UUID actorId, UUID commentId, AdminActionRequest request) {
        return moderateComment(actorId, commentId, AdminActionType.REMOVE_COMMENT, request);
    }

    @Override
    @Transactional
    public AdminActionResponse restoreComment(
            UUID actorId, UUID commentId, AdminActionRequest request) {
        return moderateComment(actorId, commentId, AdminActionType.RESTORE_COMMENT, request);
    }

    @Override
    @Transactional
    public AdminActionResponse resolveReport(
            UUID actorId, UUID reportId, AdminActionRequest request) {
        return closeReport(actorId, reportId, AdminActionType.RESOLVE_REPORT, request);
    }

    @Override
    @Transactional
    public AdminActionResponse dismissReport(
            UUID actorId, UUID reportId, AdminActionRequest request) {
        return closeReport(actorId, reportId, AdminActionType.DISMISS_REPORT, request);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminActionSummaryResponse> getActions(
            UUID adminId, AdminActionType actionType, String cursor, int size) {
        return findActions(adminId, null, actionType, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminActionResponse getActionById(UUID actionId) {
        return adminActionMapper.toResponse(
                adminActionRepository
                        .findById(actionId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.ADMIN_ACTION_NOT_FOUND)));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminActionSummaryResponse> getActionsForUser(
            UUID userId, String cursor, int size) {
        return findActions(null, userId, null, cursor, size);
    }

    private AdminActionResponse changeUserStatus(
            UUID actorId, UUID userId, AdminActionType actionType, AdminActionRequest request) {
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        UserStatus targetStatus = targetUserStatus(actionType, user.getStatus());
        user.setStatus(targetStatus);
        userRepository.save(user);
        return recordAction(
                actorId,
                actionType,
                userId,
                "user",
                userId,
                null,
                request.reason(),
                request.metadata());
    }

    private AdminActionResponse moderatePost(
            UUID actorId, UUID postId, AdminActionType actionType, AdminActionRequest request) {
        UUID ownerId =
                postRepository
                        .findOwnerIdIncludingDeleted(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        String currentStatus =
                postRepository
                        .findStatusIncludingDeleted(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        boolean restore = actionType == AdminActionType.RESTORE_POST;
        if ((restore && !PostStatus.REMOVED.toJson().equals(currentStatus))
                || (!restore && PostStatus.REMOVED.toJson().equals(currentStatus))) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        validateLinkedReport(request.reportId());
        postRepository.applyAdminModeration(
                postId,
                restore ? PostStatus.PUBLISHED.toJson() : PostStatus.REMOVED.toJson(),
                restore ? null : OffsetDateTime.now());
        return recordAction(
                actorId,
                actionType,
                ownerId,
                "post",
                postId,
                request.reportId(),
                request.reason(),
                request.metadata());
    }

    private AdminActionResponse moderateComment(
            UUID actorId, UUID commentId, AdminActionType actionType, AdminActionRequest request) {
        UUID ownerId =
                commentRepository
                        .findOwnerIdIncludingDeleted(commentId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
        boolean deleted =
                commentRepository
                        .isDeletedIncludingDeleted(commentId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
        boolean restore = actionType == AdminActionType.RESTORE_COMMENT;
        if (restore != deleted) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        validateLinkedReport(request.reportId());
        commentRepository.applyAdminModeration(commentId, restore ? null : OffsetDateTime.now());
        return recordAction(
                actorId,
                actionType,
                ownerId,
                "comment",
                commentId,
                request.reportId(),
                request.reason(),
                request.metadata());
    }

    private AdminActionResponse closeReport(
            UUID actorId, UUID reportId, AdminActionType actionType, AdminActionRequest request) {
        Report report =
                reportRepository
                        .findById(reportId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() == ReportStatus.RESOLVED
                || report.getStatus() == ReportStatus.DISMISSED) {
            throw new AppException(ApiErrorCode.REPORT_INVALID_TRANSITION);
        }
        report.setStatus(
                actionType == AdminActionType.RESOLVE_REPORT
                        ? ReportStatus.RESOLVED
                        : ReportStatus.DISMISSED);
        report.setReviewedBy(actorId);
        report.setReviewedAt(OffsetDateTime.now());
        report.setResolutionNote(request.reason().trim());
        reportRepository.save(report);
        UUID targetUserId = report.getReportType() == ReportType.USER ? report.getEntityId() : null;
        return recordAction(
                actorId,
                actionType,
                targetUserId,
                report.getReportType().toJson(),
                report.getEntityId(),
                reportId,
                request.reason(),
                request.metadata());
    }

    private CursorPageResponse<AdminActionSummaryResponse> findActions(
            UUID adminId, UUID targetUserId, AdminActionType actionType, String cursor, int size) {
        int pageSize = normalizeLimit(size);
        int queryLimit = pageSize + 1;
        ActionCursor decoded = decodeCursor(cursor);
        List<AdminAction> actions =
                adminActionRepository.findActions(
                        adminId,
                        targetUserId,
                        actionType,
                        decoded.createdAt(),
                        decoded.id(),
                        queryLimit);
        return toPage(actions, pageSize, cursor != null);
    }

    private UserStatus targetUserStatus(AdminActionType actionType, UserStatus currentStatus) {
        UserStatus target =
                switch (actionType) {
                    case BAN_USER -> UserStatus.BANNED;
                    case UNBAN_USER ->
                            currentStatus == UserStatus.BANNED
                                    ? UserStatus.ACTIVE
                                    : throwInvalidTransition();
                    case SUSPEND_USER ->
                            currentStatus == UserStatus.ACTIVE
                                    ? UserStatus.SUSPENDED
                                    : throwInvalidTransition();
                    case UNSUSPEND_USER ->
                            currentStatus == UserStatus.SUSPENDED
                                    ? UserStatus.ACTIVE
                                    : throwInvalidTransition();
                    default -> throw new AppException(ApiErrorCode.ADMIN_INVALID_ACTION);
                };
        if (target == currentStatus) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        return target;
    }

    private UserStatus throwInvalidTransition() {
        throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
    }

    private void validateLinkedReport(UUID reportId) {
        if (reportId != null && !reportRepository.existsById(reportId)) {
            throw new AppException(ApiErrorCode.REPORT_NOT_FOUND);
        }
    }

    private AdminActionResponse recordAction(
            UUID actorId,
            AdminActionType actionType,
            UUID targetUserId,
            String targetEntityType,
            UUID targetEntityId,
            UUID reportId,
            String reason,
            Map<String, Object> metadata) {
        AdminAction action =
                AdminAction.builder()
                        .adminId(actorId)
                        .actionType(actionType)
                        .targetUserId(targetUserId)
                        .targetEntityType(targetEntityType)
                        .targetEntityId(targetEntityId)
                        .reportId(reportId)
                        .reason(reason.trim())
                        .metadata(metadata)
                        .build();
        return adminActionMapper.toResponse(adminActionRepository.insert(action));
    }

    private CursorPageResponse<AdminActionSummaryResponse> toPage(
            List<AdminAction> actions, int pageSize, boolean hasPreviousPage) {
        boolean hasNextPage = actions.size() > pageSize;
        List<AdminAction> pageActions = hasNextPage ? actions.subList(0, pageSize) : actions;
        if (pageActions.isEmpty()) {
            return CursorPageResponse.<AdminActionSummaryResponse>builder()
                    .content(Collections.emptyList())
                    .pageInfo(
                            CursorPageResponse.PageInfo.builder()
                                    .hasNextPage(false)
                                    .hasPreviousPage(hasPreviousPage)
                                    .build())
                    .build();
        }
        return CursorPageResponse.<AdminActionSummaryResponse>builder()
                .content(adminActionMapper.toSummaryResponseList(pageActions))
                .pageInfo(
                        CursorPageResponse.PageInfo.builder()
                                .hasNextPage(hasNextPage)
                                .hasPreviousPage(hasPreviousPage)
                                .startCursor(encodeCursor(pageActions.get(0)))
                                .endCursor(encodeCursor(pageActions.get(pageActions.size() - 1)))
                                .build())
                .build();
    }

    private int normalizeLimit(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private String encodeCursor(AdminAction action) {
        return CursorCodec.encode(
                new Cursor(TimeCursors.toMicros(action.getCreatedAt()), action.getId()));
    }

    private ActionCursor decodeCursor(String cursor) {
        Cursor decoded = CursorCodec.decode(cursor);
        if (decoded == null) {
            return new ActionCursor(null, null);
        }
        return new ActionCursor(TimeCursors.fromMicros(decoded.sortValueMicros()), decoded.id());
    }

    private record ActionCursor(OffsetDateTime createdAt, UUID id) {

        boolean isEmpty() {
            return createdAt == null || id == null;
        }
    }
}
