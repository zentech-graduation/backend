package com.app.modules.users.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Payload for updating the authenticated user's profile")
public record UpdateProfileRequest(
        @Schema(
                        description =
                                "New unique username; letters, digits, underscores, and dots only",
                        example = "jane_doe")
                @Size(min = 3, max = 30)
                @Pattern(
                        regexp = "^[a-zA-Z0-9_.]+$",
                        message = "Username may only contain letters, digits, underscores and dots")
                String username,
        @Schema(description = "Display name shown on the profile", example = "Jane Doe")
                @Size(max = 100)
                String displayName,
        @Schema(
                        description = "Short bio (max 500 characters); send empty string to clear",
                        example = "Coffee lover")
                @Size(max = 500)
                String bio,
        @Schema(
                        description = "CDN URL of the avatar image; send empty string to clear",
                        example = "https://example.com/avatars/jane.jpg")
                @Size(max = 2048)
                String avatarUrl,
        @Schema(
                        description = "CDN URL of the banner image; send empty string to clear",
                        example = "https://example.com/banners/jane.jpg")
                @Size(max = 2048)
                String bannerUrl,
        @Schema(
                        description = "Personal website URL; send empty string to clear",
                        example = "https://luvax.online")
                @Size(max = 2048)
                String websiteUrl,
        @Schema(description = "Whether the account is private", example = "false")
                Boolean isPrivate) {}
