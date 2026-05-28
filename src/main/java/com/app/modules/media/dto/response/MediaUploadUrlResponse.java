package com.app.modules.media.dto.response;

import java.net.URL;
import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response containing a short-lived direct-upload URL for object storage. */
public record MediaUploadUrlResponse(
        @Schema(
                        description =
                                "Backend-generated object key to send back during upload confirmation.")
                String storageKey,
        @Schema(description = "Short-lived pre-signed direct-upload URL.") URL uploadUrl,
        @Schema(description = "HTTP method required for the direct upload.", example = "PUT")
                String method,
        @Schema(description = "Headers the client must include when uploading the object.")
                Map<String, String> requiredHeaders,
        @Schema(description = "Expiration instant for the pre-signed upload URL.")
                Instant expiresAt) {}
