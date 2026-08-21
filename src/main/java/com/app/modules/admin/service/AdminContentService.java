package com.app.modules.admin.service;

import java.util.UUID;

import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.response.AdminCommentSummaryResponse;
import com.app.modules.admin.dto.response.AdminPostSummaryResponse;
import com.app.modules.admin.dto.response.AdminReportTargetResponse;
import com.app.modules.report.enums.ReportType;

/**
 * Reads content for an administrative investigation, bypassing the ordinary visibility gate.
 *
 * <p>The bypass is deliberate and is the point of the surface. Investigating an account whose
 * content you can only see if that account chose to make it public is not investigating it, and a
 * removed post is precisely the one a reviewer needs to look at.
 *
 * <p>The bypass is contained rather than generalised. It lives in {@code AdminContentRepository}
 * and {@code AdminReportTargetRepository}, both of which are injected only inside this module, and
 * every route into it sits behind the moderator-or-administrator matcher. A moderator branch inside
 * {@code PostVisibilityServiceImpl} was considered and rejected: the feed, the profile listing,
 * search hydration and comment access all call that, so a branch there would make moderators see
 * private and blocked content throughout their ordinary use of the product.
 *
 * <p>Reads are logged, not audited, for the same reason the report-target read is: they happen many
 * times per investigation, and an {@code admin_actions} row for each would dilute a table whose
 * purpose is recording state changes.
 */
public interface AdminContentService {

    /**
     * Lists one account's posts, newest first, including drafts, archived posts and posts
     * moderation has removed.
     *
     * @param actorId the moderator or administrator investigating
     * @param userId the account whose posts to list
     * @param cursor opaque cursor from a previous page, or null for the first page
     * @param limit page size
     * @return a cursor page of posts
     * @throws AppException {@code USER_NOT_FOUND} when no account holds that id, live or
     *     soft-deleted; {@code INVALID_CURSOR} when the cursor was not issued by this surface
     */
    CursorPageResponse<AdminPostSummaryResponse> listPostsForUser(
            UUID actorId, UUID userId, String cursor, int limit);

    /**
     * Lists one account's comments, newest first, including ones moderation has removed and ones on
     * posts the reviewer could not otherwise see.
     *
     * @param actorId the moderator or administrator investigating
     * @param userId the account whose comments to list
     * @param cursor opaque cursor from a previous page, or null for the first page
     * @param limit page size
     * @return a cursor page of comments
     * @throws AppException {@code USER_NOT_FOUND} when no account holds that id, live or
     *     soft-deleted; {@code INVALID_CURSOR} when the cursor was not issued by this surface
     */
    CursorPageResponse<AdminCommentSummaryResponse> listCommentsForUser(
            UUID actorId, UUID userId, String cursor, int limit);

    /**
     * Loads one entity by its own identifier, whatever its visibility or soft-delete state.
     *
     * <p>Takes a bare entity identifier, which the report-anchored read deliberately refuses to.
     * That refusal was the right default while the report was the only way a moderator arrived at
     * content; it is not sufficient now that the panel links to a specific post from an audit row
     * and from an account's content listing, both of which dead-ended. The limit is the role, not
     * the anchor: every route here is behind the moderator-or-administrator matcher.
     *
     * @param actorId the moderator or administrator reviewing
     * @param entityType which kind of entity the identifier names
     * @param entityId the entity's identifier
     * @return the entity rendered for review
     * @throws AppException {@code REPORT_TARGET_NOT_FOUND} when nothing holds that id
     */
    AdminReportTargetResponse getEntity(UUID actorId, ReportType entityType, UUID entityId);
}
