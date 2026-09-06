package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Full administrative view of one account.
 *
 * <p>Adds the origin and session information an administrator needs to investigate an account to
 * the fields carried by the list shape, plus what the requesting administrator is permitted to do
 * to it. Reachable only from the ADMIN-only administrative surface.
 */
@Schema(description = "Full administrative view of one account")
public record AdminUserDetailResponse(
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
        @Schema(description = "Soft-delete timestamp, null for a live account", nullable = true)
                OffsetDateTime deletedAt,
        @Schema(
                        description =
                                "Client IP captured at account creation; null for accounts created"
                                        + " before the column existed",
                        example = "203.0.113.7",
                        nullable = true)
                String registrationIp,
        @Schema(
                        description = "Client IP of the most recent real login",
                        example = "203.0.113.7",
                        nullable = true)
                String lastLoginIp,
        @Schema(
                        description = "Most recent real login, never advanced by a refresh",
                        nullable = true)
                OffsetDateTime lastLoginAt,
        @Schema(
                        description =
                                "Moment the current suspension lapses; null when the account is not"
                                        + " suspended or the suspension is indefinite",
                        nullable = true)
                OffsetDateTime suspendedUntil,
        @Schema(description = "Live sessions, newest first")
                List<AdminUserSessionResponse> sessions,
        @Schema(description = "Most recent reports filed against this account")
                List<AdminUserReportResponse> reportsAgainst,
        @Schema(
                        description =
                                "What the requesting administrator may do to this account,"
                                        + " evaluated against the same component the write"
                                        + " endpoints enforce")
                AdminUserCapabilitiesResponse capabilities,
        @Schema(
                        description =
                                "How many warnings currently count toward this account's next"
                                        + " strike. Three of them issue a strike automatically,"
                                        + " which suspends the account, so a reviewer about to warn"
                                        + " can be told the warning will be the third before"
                                        + " issuing it. Always present, and zero for an account"
                                        + " that has never been warned. Counted by the discipline"
                                        + " service's own predicate, not recomputed here: it"
                                        + " excludes revoked warnings, warnings issued before the"
                                        + " most recent unrevoked strike, and warnings older than"
                                        + " the retention window.",
                        example = "2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long activeWarningCount) {}
