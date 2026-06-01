package com.app.modules.social.dto.response;

<<<<<<< HEAD
=======
import java.time.OffsetDateTime;
>>>>>>> 9173792e58046801ceb05ea721b53d5202d8ba28
import java.util.UUID;

import com.app.modules.social.enums.FollowStatus;

<<<<<<< HEAD
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing result of a follow action")
public record FollowResponse(
        @Schema(description = "User who initiated the follow") UUID followerId,
        @Schema(description = "User who is being followed") UUID followingId,
        @Schema(description = "Current status of the follow relationship") FollowStatus status) {}
=======
/** API response returned after creating a follow relationship or follow request. */
public record FollowResponse(
        UUID followerId, UUID followingId, FollowStatus status, OffsetDateTime createdAt) {}
>>>>>>> 9173792e58046801ceb05ea721b53d5202d8ba28
