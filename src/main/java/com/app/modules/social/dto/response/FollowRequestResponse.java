package com.app.modules.social.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.social.enums.FollowStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pending follow request detail")
public record FollowRequestResponse(
        @Schema(description = "Unique ID of the request (requester user ID)") UUID id,
        @Schema(description = "Details of the follower requesting to follow")
                SocialUserSummaryResponse follower,
        @Schema(description = "Status of the follow request") FollowStatus status,
        @Schema(description = "When the follow request was created") OffsetDateTime createdAt) {}
