package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.admin.enums.AdminActionType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contains the fields needed to display one moderation event in an audit-history list. */
@Schema(description = "Summary of an immutable moderation audit event")
public record AdminActionSummaryResponse(
        @Schema(description = "Audit event identifier") UUID id,
        @Schema(
                        description =
                                "Actor identifier, or null after the actor is permanently deleted",
                        nullable = true)
                UUID adminId,
        @Schema(description = "Moderation action type", example = "remove_post")
                AdminActionType actionType,
        @Schema(description = "Affected user identifier when applicable", nullable = true)
                UUID targetUserId,
        @Schema(description = "Polymorphic target type", example = "post") String targetEntityType,
        @Schema(description = "Polymorphic target identifier") UUID targetEntityId,
        @Schema(description = "Associated report identifier when applicable", nullable = true)
                UUID reportId,
        @Schema(description = "Human-readable moderation reason") String reason,
        @Schema(description = "UTC audit creation timestamp") OffsetDateTime createdAt) {}
