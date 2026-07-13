package com.app.modules.admin.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.CommentModerationActionRequest;
import com.app.modules.admin.dto.request.PostModerationActionRequest;
import com.app.modules.admin.dto.request.ReportResolutionActionRequest;
import com.app.modules.admin.dto.request.UserStatusActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
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
    private static final String CURSOR_SEPARATOR = "|";

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
    public AdminActionResponse updateUserStatus(
            UUID actorId, UUID userId, UserStatusActionRequest request) {
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        UserStatus targetStatus = targetUserStatus(request.actionType(), user.getStatus());
        user.setStatus(targetStatus);
        userRepository.save(user);
        return recordAction(
                actorId,
                request.actionType(),
                userId,
                "user",
                userId,
                null,
                request.reason(),
                request.metadata());
    }

    @Override
    @Transactional
    public AdminActionResponse moderatePost(
            UUID actorId, UUID postId, PostModerationActionRequest request) {
        requireAction(
                request.actionType(), AdminActionType.REMOVE_POST, AdminActionType.RESTORE_POST);
        UUID ownerId =
                postRepository
                        .findOwnerIdIncludingDeleted(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        String currentStatus =
                postRepository
                        .findStatusIncludingDeleted(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        boolean restore = request.actionType() == AdminActionType.RESTORE_POST;
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
                request.actionType(),
                ownerId,
                "post",
                postId,
                request.reportId(),
                request.reason(),
                request.metadata());
    }

    @Override
    @Transactional
    public AdminActionResponse moderateComment(
            UUID actorId, UUID commentId, CommentModerationActionRequest request) {
        requireAction(
                request.actionType(),
                AdminActionType.REMOVE_COMMENT,
                AdminActionType.RESTORE_COMMENT);
        UUID ownerId =
                commentRepository
                        .findOwnerIdIncludingDeleted(commentId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
        boolean deleted =
                commentRepository
                        .isDeletedIncludingDeleted(commentId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
        boolean restore = request.actionType() == AdminActionType.RESTORE_COMMENT;
        if (restore != deleted) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        validateLinkedReport(request.reportId());
        commentRepository.applyAdminModeration(commentId, restore ? null : OffsetDateTime.now());
        return recordAction(
                actorId,
                request.actionType(),
                ownerId,
                "comment",
                commentId,
                request.reportId(),
                request.reason(),
                request.metadata());
    }

    @Override
    @Transactional
    public AdminActionResponse resolveReport(
            UUID actorId, UUID reportId, ReportResolutionActionRequest request) {
        requireAction(
                request.actionType(),
                AdminActionType.RESOLVE_REPORT,
                AdminActionType.DISMISS_REPORT);
        Report report =
                reportRepository
                        .findById(reportId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() == ReportStatus.RESOLVED
                || report.getStatus() == ReportStatus.DISMISSED) {
            throw new AppException(ApiErrorCode.REPORT_INVALID_TRANSITION);
        }
        report.setStatus(
                request.actionType() == AdminActionType.RESOLVE_REPORT
                        ? ReportStatus.RESOLVED
                        : ReportStatus.DISMISSED);
        report.setReviewedBy(actorId);
        report.setReviewedAt(OffsetDateTime.now());
        report.setResolutionNote(request.reason().trim());
        reportRepository.save(report);
        UUID targetUserId = report.getReportType() == ReportType.USER ? report.getEntityId() : null;
        return recordAction(
                actorId,
                request.actionType(),
                targetUserId,
                report.getReportType().toJson(),
                report.getEntityId(),
                reportId,
                request.reason(),
                request.metadata());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminActionResponse> listActions(
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

    @Override
    @Transactional(readOnly = true)
    public AdminActionResponse getAction(UUID actionId) {
        return adminActionMapper.toResponse(
                adminActionRepository
                        .findById(actionId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.ADMIN_ACTION_NOT_FOUND)));
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

    private void requireAction(
            AdminActionType actual, AdminActionType first, AdminActionType second) {
        if (actual != first && actual != second) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_ACTION);
        }
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

    private CursorPageResponse<AdminActionResponse> toPage(
            List<AdminAction> actions, int pageSize, boolean hasPreviousPage) {
        boolean hasNextPage = actions.size() > pageSize;
        List<AdminAction> pageActions = hasNextPage ? actions.subList(0, pageSize) : actions;
        if (pageActions.isEmpty()) {
            return CursorPageResponse.<AdminActionResponse>builder()
                    .content(Collections.emptyList())
                    .pageInfo(
                            CursorPageResponse.PageInfo.builder()
                                    .hasNextPage(false)
                                    .hasPreviousPage(hasPreviousPage)
                                    .build())
                    .build();
        }
        return CursorPageResponse.<AdminActionResponse>builder()
                .content(adminActionMapper.toResponseList(pageActions))
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
        String raw = action.getCreatedAt() + CURSOR_SEPARATOR + action.getId();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ActionCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new ActionCursor(null, null);
        }
        try {
            String raw = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Cursor must contain createdAt and id");
            }
            return new ActionCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }

    private record ActionCursor(OffsetDateTime createdAt, UUID id) {

        boolean isEmpty() {
            return createdAt == null || id == null;
        }
    }
}
