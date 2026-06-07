package com.app.modules.social.dto.response;

import java.util.UUID;

import com.app.modules.social.enums.FollowStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing result of a follow action")
public record FollowResponse(
        @Schema(description = "User who initiated the follow") UUID followerId,
        @Schema(description = "User who is being followed") UUID followingId,
        @Schema(description = "Current status of the follow relationship") FollowStatus status,
        java.time.OffsetDateTime createdAt) {}
