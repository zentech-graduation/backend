package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The reported entity, rendered for moderation review.
 *
 * <p>One flat shape across all five report types rather than five, because a moderator reviewing a
 * queue reads the same three things every time: who made it, what it says, and what it shows.
 * Fields that do not apply to a type are null, and {@code reportType} says which those are.
 *
 * <p>Deliberately not the public {@code PostResponse}. That carries viewer state, which is
 * meaningless here: whether the reviewing moderator has liked or saved the content is not part of
 * the decision, and computing it would imply the moderator is a viewer of the content in the
 * ordinary sense, which is exactly what this endpoint is careful not to make them.
 *
 * @param reportType which kind of entity this is
 * @param entityId the entity's identifier
 * @param ownerId the account that authored it, or null once that account is deleted
 * @param ownerUsername the author's username, or null once that account is deleted
 * @param status the entity's own status where it has one: post status or account status; null for a
 *     comment, story or message, none of which carry one
 * @param text the caption, comment body, message body or profile bio, whichever applies
 * @param mediaUrls CDN URLs of the attached media, in carousel order; empty when there is none
 * @param removed whether the entity is already soft-deleted, so a moderator does not act twice
 * @param createdAt when the entity was created
 */
@Schema(description = "A reported entity rendered for moderation review")
public record AdminReportTargetResponse(
        ReportType reportType,
        UUID entityId,
        UUID ownerId,
        String ownerUsername,
        String status,
        String text,
        List<String> mediaUrls,
        boolean removed,
        OffsetDateTime createdAt) {}
