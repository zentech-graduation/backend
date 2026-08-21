package com.app.modules.admin.service;

import java.util.UUID;

import com.app.modules.admin.dto.response.AdminReportTargetResponse;

/** Domain API for reviewing the entity a report points at. */
public interface AdminReportTargetService {

    /**
     * Returns the reported entity, rendered for moderation review.
     *
     * <p>The report is the anchor and the only way in. A moderator can see exactly what somebody
     * reported, and nothing else: there is no parameter here that takes a bare entity identifier,
     * and adding one would turn this into a universal privacy bypass.
     *
     * <p>Bypasses the ordinary visibility gate on purpose, and only here. A private account's post,
     * or a post by someone who has blocked the reviewing moderator, is exactly what a moderator has
     * to be able to see once it has been reported. The alternative considered and rejected was a
     * moderator branch inside {@code PostVisibilityServiceImpl}: the feed, the profile listing,
     * search hydration and comment access all call that, so a branch there would make moderators
     * see private and blocked content throughout their ordinary use of the product.
     *
     * <p>Reads are logged, not audited. This runs many times per report, and writing each one to
     * {@code admin_actions} would dilute a table whose purpose is recording state changes.
     *
     * @param actorId moderator or administrator performing the review
     * @param reportId the report whose target to render
     * @return the entity
     * @throws com.app.common.exception.AppException with {@code REPORT_NOT_FOUND} when no report
     *     holds that id, or {@code REPORT_TARGET_GONE} when the entity it points at is gone
     */
    AdminReportTargetResponse getReportTarget(UUID actorId, UUID reportId);
}
