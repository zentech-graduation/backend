package com.app.modules.message.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response summarizing one member of a conversation. */
@Schema(description = "Conversation participant summary")
public record ParticipantResponse(
        @Schema(description = "User identifier.") UUID userId,
        @Schema(description = "Unique username.") String username,
        @Schema(description = "Display name shown on the profile.") String displayName,
        @Schema(description = "Avatar CDN URL.") String avatarUrl,
        @Schema(description = "Whether this member is a group admin.") boolean isAdmin,
        @Schema(description = "When this member joined.") OffsetDateTime joinedAt,
        @Schema(description = "When this member left, or null if still active.")
                OffsetDateTime leftAt) {}
