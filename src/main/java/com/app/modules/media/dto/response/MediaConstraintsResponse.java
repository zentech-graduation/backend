package com.app.modules.media.dto.response;

import java.util.SortedSet;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API response publishing the limits the upload path enforces.
 *
 * <p>Every value is read from the same configuration the validator reads, so a client that renders
 * these values cannot describe a rule the server does not apply.
 */
public record MediaConstraintsResponse(
        @Schema(
                        description =
                                "MIME types accepted for an image upload, in ascending order.",
                        example = "[\"image/gif\",\"image/jpeg\",\"image/png\",\"image/webp\"]")
                SortedSet<String> acceptedImageMimeTypes,
        @Schema(
                        description = "MIME types accepted for a video upload, in ascending order.",
                        example = "[\"video/mp4\",\"video/quicktime\",\"video/webm\"]")
                SortedSet<String> acceptedVideoMimeTypes,
        @Schema(
                        description =
                                "Maximum upload size in bytes. One ceiling applies to both image"
                                        + " and video.",
                        example = "104857600")
                long maxFileSizeBytes,
        @Schema(
                        description =
                                "Maximum declared video duration in seconds. Advisory: duration is"
                                        + " client-supplied and the server never reads the file, so"
                                        + " this bounds an honest client only.",
                        example = "180")
                int maxVideoDurationSeconds) {}
