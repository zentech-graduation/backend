package com.app.modules.message.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a single conversation's detail. */
@Schema(description = "Conversation detail")
public record ConversationResponse(
        @Schema(description = "Conversation identifier.") UUID id,
        @Schema(description = "True for a group conversation, false for 1-1.") boolean isGroup,
        @Schema(description = "Group display name; null for a 1-1 conversation.", nullable = true)
                String groupName,
        @Schema(description = "Group avatar CDN URL; null for a 1-1 conversation.", nullable = true)
                String groupAvatarUrl,
        @Schema(description = "User who created the conversation.", nullable = true) UUID createdBy,
        @Schema(description = "Active and former members.") List<ParticipantResponse> participants,
        @Schema(
                        description = "Creation time of the newest message; null if none yet.",
                        nullable = true)
                OffsetDateTime lastMessageAt,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt) {}
