package com.app.modules.admin.service;

import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.CommentModerationActionRequest;
import com.app.modules.admin.dto.request.PostModerationActionRequest;
import com.app.modules.admin.dto.request.ReportResolutionActionRequest;
import com.app.modules.admin.dto.request.UserStatusActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.enums.AdminActionType;

public interface AdminService {

    /**
     * Changes a user lifecycle status and records the action atomically.
     *
     * @param actorId authenticated moderator or administrator identifier
     * @param userId target user identifier
     * @param request status action and audit context
     * @return persisted audit event
     * @throws AppException when the user is missing or the action is invalid
     */
    AdminActionResponse updateUserStatus(
            UUID actorId, UUID userId, UserStatusActionRequest request);

    /**
     * Removes or restores a post and records the action atomically.
     *
     * @param actorId authenticated moderator or administrator identifier
     * @param postId target post identifier
     * @param request moderation action and audit context
     * @return persisted audit event
     * @throws AppException when the post or linked report is missing, or the action is invalid
     */
    AdminActionResponse moderatePost(
            UUID actorId, UUID postId, PostModerationActionRequest request);

    /**
     * Removes or restores a comment and records the action atomically.
     *
     * @param actorId authenticated moderator or administrator identifier
     * @param commentId target comment identifier
     * @param request moderation action and audit context
     * @return persisted audit event
     * @throws AppException when the comment or linked report is missing, or the action is invalid
     */
    AdminActionResponse moderateComment(
            UUID actorId, UUID commentId, CommentModerationActionRequest request);

    /**
     * Resolves or dismisses a report and records the action atomically.
     *
     * @param actorId authenticated moderator or administrator identifier
     * @param reportId target report identifier
     * @param request terminal action and resolution reason
     * @return persisted audit event
     * @throws AppException when the report is missing, terminal, or the action is invalid
     */
    AdminActionResponse resolveReport(
            UUID actorId, UUID reportId, ReportResolutionActionRequest request);

    /**
     * Lists immutable audit events with optional filters and keyset pagination.
     *
     * @param adminId optional actor filter
     * @param targetUserId optional affected-user filter
     * @param actionType optional action-type filter
     * @param cursor opaque cursor from the prior page
     * @param size requested page size
     * @return matching audit event page
     */
    CursorPageResponse<AdminActionResponse> listActions(
            UUID adminId, UUID targetUserId, AdminActionType actionType, String cursor, int size);

    /**
     * Returns one immutable audit event.
     *
     * @param actionId audit event identifier
     * @return audit event details
     * @throws AppException when the audit event does not exist
     */
    AdminActionResponse getAction(UUID actionId);
}
