package com.app.modules.message.dto.response;

import java.util.UUID;

import com.app.modules.media.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Media attached to a message, resolved server-side.
 *
 * <p>A message previously exposed only {@code mediaAssetId}, and the media module publishes upload,
 * upload-complete, and constraints but no lookup by id. A recipient therefore held an opaque
 * identifier and had no way to render the attachment. Resolving the asset here is the approach the
 * post module already takes for its own media.
 *
 * <p>Deliberately module-local rather than reusing {@code PostMediaResponse}: that record carries
 * {@code position} and {@code altText}, which describe a post's carousel and mean nothing on a
 * message, and omits {@code duration}, which a message video player needs.
 */
@Schema(description = "Media attached to a message")
public record MessageMediaResponse(
        @Schema(description = "Identifier of the underlying media asset.") UUID mediaAssetId,
        @Schema(description = "Image or video.") MediaType mediaType,
        @Schema(description = "Publicly reachable CDN URL.") String cdnUrl,
        @Schema(description = "Pixel width; null when unknown.", nullable = true) Integer width,
        @Schema(description = "Pixel height; null when unknown.", nullable = true) Integer height,
        @Schema(description = "Duration in seconds for video; null for an image.", nullable = true)
                Integer duration,
        @Schema(description = "Blurhash placeholder; null when unknown.", nullable = true)
                String blurhash) {}
