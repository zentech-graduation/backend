package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One account as it appears in an administrative list or search result.
 *
 * <p>Carries {@code email} and {@code deletedAt}, neither of which appears on any public user
 * shape. Reachable only from the ADMIN-only administrative surface.
 */
@Schema(description = "One account in an administrative list or search result")
public record AdminUserListItemResponse(
        @Schema(description = "Account identifier") UUID id,
        @Schema(description = "Unique username", example = "john_doe") String username,
        @Schema(description = "Registered email address", example = "john@example.com")
                String email,
        @Schema(description = "Display name shown on the profile", nullable = true)
                String displayName,
        @Schema(description = "Account role", example = "user") UserRole role,
        @Schema(description = "Account lifecycle status", example = "active") UserStatus status,
        @Schema(description = "Whether the account carries a verified badge") boolean isVerified,
        @Schema(description = "Whether the account is private") boolean isPrivate,
        @Schema(description = "Account creation timestamp") OffsetDateTime createdAt,
        @Schema(
                        description = "Most recent real login, never advanced by a refresh",
                        nullable = true)
                OffsetDateTime lastLoginAt,
        @Schema(description = "Soft-delete timestamp, null for a live account", nullable = true)
                OffsetDateTime deletedAt) {}
