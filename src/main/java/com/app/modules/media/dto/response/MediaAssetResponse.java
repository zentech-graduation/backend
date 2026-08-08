package com.app.modules.media.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.media.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response containing persisted media asset metadata. */
public record MediaAssetResponse(
        @Schema(description = "Media asset identifier.") UUID id,
        @Schema(description = "Owner user identifier.") UUID userId,
        @Schema(description = "Object storage key.") String storageKey,
        @Schema(description = "CDN URL derived from the configured CDN base URL.") String cdnUrl,
        @Schema(description = "Media category.") MediaType mediaType,
        @Schema(description = "Uploaded object MIME type.") String mimeType,
        @Schema(description = "Uploaded object size in bytes.") long fileSize,
        @Schema(description = "Media width in pixels.", nullable = true) Integer width,
        @Schema(description = "Media height in pixels.", nullable = true) Integer height,
        @Schema(description = "Video duration in seconds, or null for images.", nullable = true)
                Integer duration,
        @Schema(description = "Optional client-generated blurhash preview.", nullable = true)
                String blurhash,
        @Schema(description = "Database creation timestamp.") OffsetDateTime createdAt) {}
