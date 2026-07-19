package com.app.modules.story.dto.response;

import java.util.UUID;

import com.app.modules.media.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for the single media asset backing a story. */
@Schema(description = "Media asset rendered by a story")
public record StoryMediaResponse(
        @Schema(description = "Media asset identifier.") UUID mediaAssetId,
        @Schema(description = "CDN URL of the asset.") String cdnUrl,
        @Schema(description = "Asset media type.") MediaType mediaType,
        @Schema(description = "Pixel width, when known.") Integer width,
        @Schema(description = "Pixel height, when known.") Integer height,
        @Schema(description = "Video duration in seconds; null for images.") Integer duration,
        @Schema(description = "Blurhash placeholder string.") String blurhash) {}
