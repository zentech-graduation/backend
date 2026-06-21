package com.app.modules.post.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response summarizing a user who liked a post. */
@Schema(description = "User summary for a post liker")
public record LikerResponse(
        @Schema(description = "User identifier.") UUID userId,
        @Schema(description = "Unique username.") String username,
        @Schema(description = "Display name shown on the profile.") String displayName,
        @Schema(description = "Avatar CDN URL.") String avatarUrl,
        @Schema(description = "Verified badge flag.") boolean isVerified) {}
