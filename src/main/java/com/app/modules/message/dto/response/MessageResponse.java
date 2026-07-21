package com.app.modules.message.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.message.enums.MessageType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a single message, including a tombstoned one. */
@Schema(description = "A message within a conversation")
public record MessageResponse(
        @Schema(description = "Message identifier.") UUID id,
        @Schema(description = "Conversation the message belongs to.") UUID conversationId,
        @Schema(description = "Sender; null if the sending user's account was deleted.")
                UUID senderId,
        @Schema(description = "Content kind of the message.") MessageType messageType,
        @Schema(description = "Text body; null for a non-text message or a deleted message.")
                String content,
        @Schema(description = "Referenced media asset for an image/video message.")
                UUID mediaAssetId,
        @Schema(description = "Shared post for a post-share message.") UUID sharedPostId,
        @Schema(description = "Shared story for a story-share message.") UUID sharedStoryId,
        @Schema(description = "Message this one replies to, or null.") UUID replyToId,
        @Schema(description = "True if the sender deleted this message.") boolean isDeleted,
        @Schema(description = "Deletion timestamp; null unless deleted.") OffsetDateTime deletedAt,
        @Schema(description = "Creation timestamp.") OffsetDateTime createdAt) {}
