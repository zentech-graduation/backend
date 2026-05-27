package com.app.modules.users.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public-facing profile of another user.
 *
 * <p>Note on {@code isVerified}: this is the administrator-granted platform badge ({@code
 * users.is_verified}), not email confirmation status.
 */
@Schema(description = "Public profile of a user")
public record PublicUserProfileResponse(
        @Schema(
                        description = "Unique user identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID id,
        @Schema(description = "Unique username", example = "jane_doe") String username,
        @Schema(description = "Display name", example = "Jane Doe") String displayName,
        @Schema(description = "Short bio") String bio,
        @Schema(description = "Avatar CDN URL") String avatarUrl,
        @Schema(description = "Personal website URL") String websiteUrl,
        @Schema(description = "Whether the account is private", example = "false")
                boolean isPrivate,
        @Schema(description = "Whether the account has a verified badge", example = "false")
                boolean isVerified,
        @Schema(
                        description = "Number of followers; null when caller is unauthenticated",
                        example = "120")
                Integer followerCount,
        @Schema(
                        description =
                                "Number of accounts followed; null when caller is unauthenticated",
                        example = "80")
                Integer followingCount,
        @Schema(
                        description =
                                "Number of published posts; null when caller is unauthenticated",
                        example = "15")
                Integer postCount,
        @Schema(description = "Account creation timestamp") OffsetDateTime createdAt) {}
