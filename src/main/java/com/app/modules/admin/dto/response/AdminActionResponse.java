package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.app.modules.admin.enums.AdminActionType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contains one immutable moderation audit event and its target context. */
@Schema(description = "Immutable moderation audit event")
public record AdminActionResponse(
        @Schema(
                        description = "Audit event identifier",
                        example = "8eab6be1-e8aa-4a4a-b61d-e9916634f95d")
                UUID id,
        @Schema(description = "Actor identifier, or null after the actor is permanently deleted")
                UUID adminId,
        @Schema(description = "Moderation action type", example = "remove_post")
                AdminActionType actionType,
        @Schema(description = "Affected user identifier when applicable") UUID targetUserId,
        @Schema(description = "Polymorphic target type", example = "post") String targetEntityType,
        @Schema(description = "Polymorphic target identifier") UUID targetEntityId,
        @Schema(description = "Associated report identifier when applicable") UUID reportId,
        @Schema(description = "Human-readable moderation reason") String reason,
        @Schema(description = "Structured moderation context") Map<String, Object> metadata,
        @Schema(description = "UTC audit creation timestamp") OffsetDateTime createdAt) {}
