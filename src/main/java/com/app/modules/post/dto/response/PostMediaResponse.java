package com.app.modules.post.dto.response;

import java.util.UUID;

import com.app.modules.media.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a single ordered media item of a post, hydrated from {@code media_assets}. */
@Schema(description = "Ordered media item of a post with rendering metadata")
public record PostMediaResponse(
        @Schema(description = "Post media row identifier.") UUID id,
        @Schema(description = "Referenced media asset identifier.") UUID mediaAssetId,
        @Schema(description = "Zero-based carousel position.") short position,
        @Schema(description = "Accessibility alt text.") String altText,
        @Schema(description = "Public CDN URL of the media object.") String cdnUrl,
        @Schema(description = "Media category.") MediaType mediaType,
        @Schema(description = "Media width in pixels.") Integer width,
        @Schema(description = "Media height in pixels.") Integer height,
        @Schema(description = "Client-generated blurhash preview.") String blurhash) {}
