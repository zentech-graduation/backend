package com.app.modules.social.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User profile summary in social lists")
public record SocialUserSummaryResponse(
        @Schema(description = "Unique user identifier") UUID id,
        @Schema(description = "Unique username") String username,
        @Schema(description = "Display name") String displayName,
        @Schema(description = "URL to user's avatar image") String avatarUrl,
        @Schema(description = "Whether the user is verified") boolean isVerified) {}
