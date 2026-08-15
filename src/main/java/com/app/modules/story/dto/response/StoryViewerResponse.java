package com.app.modules.story.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response summarizing a user who viewed a story. */
@Schema(description = "User summary for a story viewer")
public record StoryViewerResponse(
        @Schema(description = "Viewer user identifier.") UUID viewerId,
        @Schema(description = "Unique username.") String username,
        @Schema(description = "Display name shown on the profile.", nullable = true)
                String displayName,
        @Schema(description = "Avatar CDN URL.", nullable = true) String avatarUrl,
        @Schema(description = "When the viewer saw the story.") OffsetDateTime viewedAt) {}
