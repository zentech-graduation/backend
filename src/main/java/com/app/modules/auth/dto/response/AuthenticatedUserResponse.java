package com.app.modules.auth.dto.response;

import java.util.UUID;

import com.app.modules.users.enums.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;

/** Authenticated user identity returned inside authentication responses. */
@Schema(description = "Minimal user information included in authentication responses")
public record AuthenticatedUserResponse(
        @Schema(
                        description = "Unique user identifier",
                        example = "550e8400-e29b-41d4-a716-446655440000")
                UUID id,
        @Schema(description = "Unique username", example = "john_doe") String username,
        @Schema(description = "User's email address", example = "john@example.com") String email,
        @Schema(
                        description = "Display name shown on the profile",
                        example = "John Doe",
                        nullable = true)
                String displayName,
        @Schema(description = "User's role in the system", example = "user") UserRole role,
        @Schema(description = "Whether the user has completed email verification", example = "true")
                boolean emailVerified) {}
