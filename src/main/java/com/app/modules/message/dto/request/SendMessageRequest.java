package com.app.modules.message.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.message.enums.MessageType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for sending a message. Which fields are required depends on {@code messageType}:
 * {@code text} requires {@code content} and no media/shared reference; {@code image}/{@code video}
 * require {@code mediaAssetId}; {@code post_share} requires {@code sharedPostId}; {@code
 * story_share} requires {@code sharedStoryId}.
 */
@Schema(description = "Message send payload")
public record SendMessageRequest(
        @Schema(description = "Content kind of the message.") @NotNull MessageType messageType,
        @Schema(description = "Text body; required for a text message.") @Size(max = 4000)
                String content,
        @Schema(description = "Referenced media asset; required for image/video.")
                UUID mediaAssetId,
        @Schema(description = "Shared post; required for a post share.") UUID sharedPostId,
        @Schema(description = "Shared story; required for a story share.") UUID sharedStoryId,
        @Schema(description = "Message this one replies to; must be in the same conversation.")
                UUID replyToId) {}
