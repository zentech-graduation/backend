package com.app.modules.admin.service;

import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminActionSummaryResponse;
import com.app.modules.admin.enums.AdminActionType;

public interface AdminService {

    /** Bans a user and records the action atomically. */
    AdminActionResponse banUser(UUID actorId, UUID userId, AdminActionRequest request);

    /** Unbans a banned user and records the action atomically. */
    AdminActionResponse unbanUser(UUID actorId, UUID userId, AdminActionRequest request);

    /** Suspends an active user and records the action atomically. */
    AdminActionResponse suspendUser(UUID actorId, UUID userId, AdminActionRequest request);

    /** Unsuspends a suspended user and records the action atomically. */
    AdminActionResponse unsuspendUser(UUID actorId, UUID userId, AdminActionRequest request);

    /** Removes a post and records the action atomically. */
    AdminActionResponse removePost(UUID actorId, UUID postId, AdminActionRequest request);

    /** Restores a removed post and records the action atomically. */
    AdminActionResponse restorePost(UUID actorId, UUID postId, AdminActionRequest request);

    /** Removes a comment and records the action atomically. */
    AdminActionResponse removeComment(UUID actorId, UUID commentId, AdminActionRequest request);

    /** Restores a removed comment and records the action atomically. */
    AdminActionResponse restoreComment(UUID actorId, UUID commentId, AdminActionRequest request);

    /** Resolves a pending report and records the action atomically. */
    AdminActionResponse resolveReport(UUID actorId, UUID reportId, AdminActionRequest request);

    /** Dismisses a pending report and records the action atomically. */
    AdminActionResponse dismissReport(UUID actorId, UUID reportId, AdminActionRequest request);

    /** Lists audit-event summaries with optional actor and action-type filters. */
    CursorPageResponse<AdminActionSummaryResponse> getActions(
            UUID adminId, AdminActionType actionType, String cursor, int size);

    /**
     * Returns one immutable audit event.
     *
     * @throws AppException when the audit event does not exist
     */
    AdminActionResponse getActionById(UUID actionId);

    /** Lists audit-event summaries for one affected user. */
    CursorPageResponse<AdminActionSummaryResponse> getActionsForUser(
            UUID userId, String cursor, int size);
}
