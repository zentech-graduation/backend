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

    /**
     * Lists audit-event summaries with optional actor and action-type filters.
     *
     * <p>A moderator sees only rows it authored, whatever {@code adminId} filter it supplies. An
     * administrator sees every row. The restriction is applied here rather than at the web layer so
     * it holds for any caller of this method.
     *
     * @param actorId the requesting account, resolved from the security context
     * @param adminId actor filter requested by the caller; ignored for a moderator
     * @param actionType action-type filter, or null for every type
     * @param cursor opaque keyset cursor, or null for the first page
     * @param size requested page size
     * @return one cursor page of audit summaries visible to this actor
     */
    CursorPageResponse<AdminActionSummaryResponse> getActions(
            UUID actorId, UUID adminId, AdminActionType actionType, String cursor, int size);

    /**
     * Returns one immutable audit event.
     *
     * <p>A moderator may read only a row it authored. A row authored by someone else is reported as
     * absent rather than forbidden, so the endpoint does not confirm that an audit row it may not
     * read exists.
     *
     * @param actorId the requesting account, resolved from the security context
     * @param actionId the audit event to read
     * @return the audit event
     * @throws AppException when the audit event does not exist or is not visible to this actor
     */
    AdminActionResponse getActionById(UUID actorId, UUID actionId);

    /**
     * Lists audit-event summaries for one affected user.
     *
     * <p>A moderator sees only rows it authored against that user; an administrator sees every row.
     *
     * @param actorId the requesting account, resolved from the security context
     * @param userId the affected account
     * @param cursor opaque keyset cursor, or null for the first page
     * @param size requested page size
     * @return one cursor page of audit summaries visible to this actor
     */
    CursorPageResponse<AdminActionSummaryResponse> getActionsForUser(
            UUID actorId, UUID userId, String cursor, int size);
}
