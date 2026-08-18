package com.app.modules.message.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response summarizing one member of a conversation. */
@Schema(description = "Conversation participant summary")
public record ParticipantResponse(
        @Schema(description = "User identifier.") UUID userId,
        @Schema(description = "Unique username.", nullable = true) String username,
        @Schema(description = "Display name shown on the profile.", nullable = true)
                String displayName,
        @Schema(description = "Avatar CDN URL.", nullable = true) String avatarUrl,
        @Schema(description = "When this member joined.") OffsetDateTime joinedAt,
        @Schema(description = "When this member left, or null if still active.", nullable = true)
                OffsetDateTime leftAt) {}
