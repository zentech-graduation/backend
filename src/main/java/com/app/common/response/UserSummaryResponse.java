package com.app.common.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Shared public projection of a user, embedded wherever a response references an author or actor.
 *
 * <p>Carries only publicly renderable identity fields. It never carries email, role, or account
 * status; those are not the concern of a reader rendering someone else's content. A soft-deleted or
 * unknown user resolves to a placeholder whose {@code username} is null and whose {@code
 * displayName} is a fixed marker, so a client never has to branch on a missing author.
 */
@Schema(description = "Public identity summary of a user embedded as an author or actor")
public record UserSummaryResponse(
        @Schema(
                        description = "Unique user identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID id,
        @Schema(description = "Unique username; null when the user is deleted or unknown")
                String username,
        @Schema(description = "Display name shown on the profile", example = "John Doe")
                String displayName,
        @Schema(description = "Avatar CDN URL; null when absent") String avatarUrl,
        @Schema(description = "Verified badge flag", example = "false") boolean isVerified) {}
