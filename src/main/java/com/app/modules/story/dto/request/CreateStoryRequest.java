package com.app.modules.story.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for creating a story.
 *
 * <p>The story type is derived server-side from the media asset's type; the client only supplies
 * the asset reference and an optional caption.
 */
@Schema(description = "Story creation payload")
public record CreateStoryRequest(
        @Schema(description = "Identifier of an uploaded media asset owned by the caller.")
                @NotNull(message = "mediaId is required")
                UUID mediaId,
        @Schema(description = "Optional caption overlaid on the story.")
                @Size(max = 2200, message = "Caption must be at most 2200 characters")
                String caption) {}
