package com.app.modules.admin.service.impl;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.Cursor;
import com.app.common.pagination.CursorCodec;
import com.app.common.pagination.CursorScope;
import com.app.common.pagination.TimeCursors;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminEscalateReportRequest;
import com.app.modules.admin.dto.request.AdminSuspendUserRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminActionSummaryResponse;
import com.app.modules.admin.dto.response.AdminPostRestoreResponse;
import com.app.modules.admin.dto.response.EscalatedReportCountResponse;
import com.app.modules.admin.entity.AdminAction;
import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.admin.mapper.AdminActionMapper;
import com.app.modules.admin.repository.AdminActionRepository;
import com.app.modules.admin.service.AdminActionRecorder;
import com.app.modules.admin.service.AdminAuthorizationService;
import com.app.modules.admin.service.AdminService;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.message.repository.MessageRepository;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostModerationResult;
import com.app.modules.post.service.PostService;
import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.story.repository.StoryRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
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
    private final PostService postService;
    private final CommentRepository commentRepository;
    private final StoryRepository storyRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final AdminActionMapper adminActionMapper;
    private final AdminActionRecorder adminActionRecorder;
    private final AdminAuthorizationService adminAuthorizationService;

    public AdminServiceImpl(
            AdminActionRepository adminActionRepository,
            UserRepository userRepository,
            PostRepository postRepository,
            PostService postService,
            CommentRepository commentRepository,
            StoryRepository storyRepository,
            MessageRepository messageRepository,
            ReportRepository reportRepository,
            AdminActionMapper adminActionMapper,
            AdminActionRecorder adminActionRecorder,
            AdminAuthorizationService adminAuthorizationService) {
        this.adminActionRepository = adminActionRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.commentRepository = commentRepository;
        this.storyRepository = storyRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.adminActionMapper = adminActionMapper;
        this.adminActionRecorder = adminActionRecorder;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Override
    @Transactional
    public AdminActionResponse banUser(UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(actorId, userId, AdminActionType.BAN_USER, request.reason(), null);
    }

    @Override
    @Transactional
    public AdminActionResponse unbanUser(UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(
                actorId, userId, AdminActionType.UNBAN_USER, request.reason(), null);
    }

    @Override
    @Transactional
    public AdminActionResponse suspendUser(
            UUID actorId, UUID userId, AdminSuspendUserRequest request) {
        OffsetDateTime suspendedUntil =
                request.durationDays() == null
                        ? null
                        : OffsetDateTime.now().plusDays(request.durationDays());
        return changeUserStatus(
                actorId, userId, AdminActionType.SUSPEND_USER, request.reason(), suspendedUntil);
    }

    @Override
    @Transactional
    public AdminActionResponse unsuspendUser(
            UUID actorId, UUID userId, AdminActionRequest request) {
        return changeUserStatus(
                actorId, userId, AdminActionType.UNSUSPEND_USER, request.reason(), null);
    }

    @Override
    @Transactional
    public AdminActionResponse removePost(UUID actorId, UUID postId, AdminActionRequest request) {
        return moderatePostAndReport(actorId, postId, AdminActionType.REMOVE_POST, request)
                .action();
    }

    @Override
    @Transactional
    public AdminPostRestoreResponse restorePost(
            UUID actorId, UUID postId, AdminActionRequest request) {
        ModerationOutcome outcome =
                moderatePostAndReport(actorId, postId, AdminActionType.RESTORE_POST, request);
        return new AdminPostRestoreResponse(outcome.action(), outcome.remainingBannedHashtags());
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
    public AdminActionResponse removeStory(UUID actorId, UUID storyId, AdminActionRequest request) {
        return moderateStory(actorId, storyId, AdminActionType.REMOVE_STORY, request);
    }

    @Override
    @Transactional
    public AdminActionResponse restoreStory(
            UUID actorId, UUID storyId, AdminActionRequest request) {
        return moderateStory(actorId, storyId, AdminActionType.RESTORE_STORY, request);
    }

    @Override
    @Transactional
    public AdminActionResponse removeMessage(
            UUID actorId, UUID messageId, AdminActionRequest request) {
        return moderateMessage(actorId, messageId, AdminActionType.REMOVE_MESSAGE, request);
    }

    @Override
    @Transactional
    public AdminActionResponse restoreMessage(
            UUID actorId, UUID messageId, AdminActionRequest request) {
        return moderateMessage(actorId, messageId, AdminActionType.RESTORE_MESSAGE, request);
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
    @Transactional
    public AdminActionResponse escalateReport(
            UUID actorId, UUID reportId, AdminEscalateReportRequest request) {
        Report report =
                reportRepository
                        .findById(reportId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_NOT_FOUND));
        // Only an open report can be escalated. Escalating a closed one would reopen a decision
        // already taken, and escalating an escalated one would rewrite whose escalation it was.
        if (report.getStatus() != ReportStatus.PENDING
                && report.getStatus() != ReportStatus.REVIEWING) {
            throw new AppException(ApiErrorCode.REPORT_INVALID_TRANSITION);
        }
        report.setStatus(ReportStatus.ESCALATED);
        report.setEscalatedBy(actorId);
        report.setEscalatedAt(OffsetDateTime.now());
        report.setEscalationReason(request.reason().trim());
        reportRepository.save(report);
        log.info("Report escalated: actorId={}, reportId={}", actorId, reportId);
        UUID targetUserId = report.getReportType() == ReportType.USER ? report.getEntityId() : null;
        return adminActionRecorder.record(
                actorId,
                AdminActionType.ESCALATE_REPORT,
                targetUserId,
                report.getReportType().toJson(),
                report.getEntityId(),
                reportId,
                request.reason(),
                null);
    }

    @Override
    @Transactional(readOnly = true)
    public EscalatedReportCountResponse countEscalatedReports() {
        return new EscalatedReportCountResponse(
                reportRepository.countByStatus(ReportStatus.ESCALATED));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminActionSummaryResponse> getActions(
            UUID actorId, UUID adminId, AdminActionType actionType, String cursor, int size) {
        UUID effectiveAdminId = scopeActorFilter(actorId, adminId);
        return findActions(
                effectiveAdminId, null, actionType, cursor, size, CursorScope.ADMIN_ACTIONS);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminActionResponse getActionById(UUID actorId, UUID actionId) {
        AdminAction action =
                adminActionRepository
                        .findById(actionId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.ADMIN_ACTION_NOT_FOUND));
        // Reported as absent rather than forbidden. A 403 here would confirm that an audit row this
        // moderator may not read exists, which is the same disclosure the list filter prevents.
        if (isModerator(actorId) && !actorId.equals(action.getAdminId())) {
            throw new AppException(ApiErrorCode.ADMIN_ACTION_NOT_FOUND);
        }
        return adminActionMapper.toResponse(action);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminActionSummaryResponse> getActionsForUser(
            UUID actorId, UUID userId, String cursor, int size) {
        UUID effectiveAdminId = scopeActorFilter(actorId, null);
        return findActions(
                effectiveAdminId, userId, null, cursor, size, CursorScope.ADMIN_ACTIONS_FOR_USER);
    }

    /**
     * Narrows an audit read to the caller's own rows when the caller is a moderator.
     *
     * <p>Returns the caller's id for a moderator, discarding whatever {@code adminId} the caller
     * asked for, and the requested filter unchanged for an administrator. The actor's role is read
     * from the source of truth rather than from a token claim, for the same reason every other
     * authorization decision in this service is: a claim minted before a demotion is stale.
     */
    private UUID scopeActorFilter(UUID actorId, UUID requestedAdminId) {
        return isModerator(actorId) ? actorId : requestedAdminId;
    }

    private boolean isModerator(UUID actorId) {
        return userRepository
                        .findByIdAndDeletedAtIsNull(actorId)
                        .map(User::getRole)
                        .orElseThrow(() -> new AppException(ApiErrorCode.FORBIDDEN))
                == UserRole.MODERATOR;
    }

    private AdminActionResponse changeUserStatus(
            UUID actorId,
            UUID userId,
            AdminActionType actionType,
            String reason,
            OffsetDateTime suspendedUntil) {
        // The actor's role is read from the source of truth rather than taken from the caller or
        // from a token claim: a claim minted before a demotion is stale, and a caller-supplied role
        // would make the guard advisory for any future non-controller caller.
        UserRole actorRole =
                userRepository
                        .findByIdAndDeletedAtIsNull(actorId)
                        .map(User::getRole)
                        .orElseThrow(() -> new AppException(ApiErrorCode.FORBIDDEN));
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        // Checked against the entity already loaded above, inside this transaction; a second read
        // of the target's role would be a race against a concurrent role change.
        adminAuthorizationService.assertMayChangeUserStatus(actorId, actorRole, user);
        UserStatus targetStatus = targetUserStatus(actionType, user.getStatus());
        user.setStatus(targetStatus);
        // Written on every status change, not only on suspend. Any transition out of 'suspended'
        // passes null here, which is what stops the reinstatement sweep from firing on a row an
        // administrator has already handled.
        user.setSuspendedUntil(targetStatus == UserStatus.SUSPENDED ? suspendedUntil : null);
        userRepository.save(user);
        return adminActionRecorder.record(
                actorId, actionType, userId, "user", userId, null, reason, null);
    }

    // The mutation itself is deliberately not performed here. PostService owns every side effect
    // of a removal, so the administrative path and the owner path cannot drift apart again the way
    // they had: this method used to write the row directly and left the hashtag associations and
    // the search-index document behind.
    private ModerationOutcome moderatePostAndReport(
            UUID actorId, UUID postId, AdminActionType actionType, AdminActionRequest request) {
        String currentStatus =
                postRepository
                        .findStatusIncludingDeleted(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        boolean restore = actionType == AdminActionType.RESTORE_POST;
        if (restore != PostStatus.REMOVED.toJson().equals(currentStatus)) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        validateLinkedReport(request.reportId());
        PostModerationResult result =
                restore
                        ? postService.applyModerationRestore(postId)
                        : postService.applyModerationRemoval(postId);
        // Server-derived facts only, which is the whole contract AdminActionRecorder enforces. The
        // stripped names are recorded when a restore re-derived a caption naming a banned hashtag:
        // the post came back without that association, and the audit row is where that shows.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resultingStatus", result.status().toJson());
        if (!result.remainingBannedHashtags().isEmpty()) {
            metadata.put("remainingBannedHashtags", result.remainingBannedHashtags());
        }
        AdminActionResponse action =
                adminActionRecorder.record(
                        actorId,
                        actionType,
                        result.ownerId(),
                        "post",
                        postId,
                        request.reportId(),
                        request.reason(),
                        metadata);
        return new ModerationOutcome(action, result.remainingBannedHashtags());
    }

    // The audit row plus the one side effect a moderator has to be told about. The audit row alone
    // cannot carry it in a declared shape: metadata is a free-form map shared by every action.
    private record ModerationOutcome(
            AdminActionResponse action, List<String> remainingBannedHashtags) {}

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
        return adminActionRecorder.record(
                actorId,
                actionType,
                ownerId,
                "comment",
                commentId,
                request.reportId(),
                request.reason(),
                null);
    }

    // Shaped on moderateComment rather than on the post path: a story removal has no side effect
    // beyond the row itself, so there is no owning-service method for it to delegate to.
    private AdminActionResponse moderateStory(
            UUID actorId, UUID storyId, AdminActionType actionType, AdminActionRequest request) {
        UUID ownerId =
                storyRepository
                        .findOwnerIdIncludingDeleted(storyId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.STORY_NOT_FOUND));
        boolean deleted =
                storyRepository
                        .isDeletedIncludingDeleted(storyId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.STORY_NOT_FOUND));
        boolean restore = actionType == AdminActionType.RESTORE_STORY;
        if (restore != deleted) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        validateLinkedReport(request.reportId());
        storyRepository.applyAdminModeration(storyId, restore ? null : OffsetDateTime.now());
        return adminActionRecorder.record(
                actorId,
                actionType,
                ownerId,
                "story",
                storyId,
                request.reportId(),
                request.reason(),
                null);
    }

    // The transition guard reads admin_removed_at and not is_deleted, so a message the sender
    // deleted is not mistaken for one a moderator removed. Without that split a restore would
    // reverse the sender's own deletion and put back a message whose content the sender destroyed.
    private AdminActionResponse moderateMessage(
            UUID actorId, UUID messageId, AdminActionType actionType, AdminActionRequest request) {
        boolean removed =
                messageRepository
                        .isAdminRemoved(messageId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.MESSAGE_NOT_FOUND));
        // Null for a message whose sender's account was deleted (V32), which is a legitimate
        // moderation target: the audit row then simply carries no target user.
        UUID senderId = messageRepository.findSenderIdForModeration(messageId).orElse(null);
        boolean restore = actionType == AdminActionType.RESTORE_MESSAGE;
        if (restore != removed) {
            throw new AppException(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        validateLinkedReport(request.reportId());
        messageRepository.applyAdminModeration(messageId, restore ? null : OffsetDateTime.now());
        return adminActionRecorder.record(
                actorId,
                actionType,
                senderId,
                "message",
                messageId,
                request.reportId(),
                request.reason(),
                null);
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
        // Checked here rather than by a path matcher, because resolve and dismiss are one shared
        // path for every report and only the report's own state decides who may close it. A
        // moderator escalated this one because it did not want to decide it; letting a moderator
        // close it anyway would make the escalation an empty gesture.
        if (report.getStatus() == ReportStatus.ESCALATED && isModerator(actorId)) {
            throw new AppException(ApiErrorCode.FORBIDDEN);
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
        return adminActionRecorder.record(
                actorId,
                actionType,
                targetUserId,
                report.getReportType().toJson(),
                report.getEntityId(),
                reportId,
                request.reason(),
                null);
    }

    private CursorPageResponse<AdminActionSummaryResponse> findActions(
            UUID adminId,
            UUID targetUserId,
            AdminActionType actionType,
            String cursor,
            int size,
            String scope) {
        int pageSize = normalizeLimit(size);
        int queryLimit = pageSize + 1;
        ActionCursor decoded = decodeCursor(cursor, scope);
        List<AdminAction> actions =
                adminActionRepository.findActions(
                        adminId,
                        targetUserId,
                        actionType,
                        decoded.createdAt(),
                        decoded.id(),
                        queryLimit);
        return toPage(actions, pageSize, cursor != null, scope);
    }

    // UserStatus.DEACTIVATED is deliberately absent from this mapping and is unreachable today. It
    // is reserved for a future self-service account-deactivation flow, which would be a user action
    // rather than a moderation one, so it would not enter through an admin_action_type at all.
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

    private CursorPageResponse<AdminActionSummaryResponse> toPage(
            List<AdminAction> actions, int pageSize, boolean hasPreviousPage, String scope) {
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
                                .startCursor(encodeCursor(pageActions.get(0), scope))
                                .endCursor(
                                        encodeCursor(
                                                pageActions.get(pageActions.size() - 1), scope))
                                .build())
                .build();
    }

    private int normalizeLimit(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private String encodeCursor(AdminAction action, String scope) {
        return CursorCodec.encode(
                new Cursor(TimeCursors.toMicros(action.getCreatedAt()), action.getId()), scope);
    }

    private ActionCursor decodeCursor(String cursor, String scope) {
        Cursor decoded = CursorCodec.decode(cursor, scope);
        if (decoded == null) {
            return new ActionCursor(null, null);
        }
        return new ActionCursor(TimeCursors.fromMicros(decoded.sortValueMicros()), decoded.id());
    }

    private record ActionCursor(OffsetDateTime createdAt, UUID id) {}
}
