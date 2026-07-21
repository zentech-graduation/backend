package com.app.modules.message.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for one row of the caller's conversation list. */
@Schema(description = "Conversation list entry")
public record ConversationSummaryResponse(
        @Schema(description = "Conversation identifier.") UUID id,
        @Schema(description = "True for a group conversation, false for 1-1.") boolean isGroup,
        @Schema(description = "Group display name; null for a 1-1 conversation.") String groupName,
        @Schema(description = "Group avatar CDN URL; null for a 1-1 conversation.")
                String groupAvatarUrl,
        @Schema(description = "Active members.") List<ParticipantResponse> participants,
        @Schema(description = "Number of unread messages for the caller.") long unreadCount,
        @Schema(description = "Creation time of the newest message; null if none yet.")
                OffsetDateTime lastMessageAt) {}
