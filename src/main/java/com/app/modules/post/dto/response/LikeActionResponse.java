package com.app.modules.post.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a like or unlike action with the fresh trigger-maintained counter. */
@Schema(description = "Result of a like or unlike action")
public record LikeActionResponse(
        @Schema(description = "Affected post identifier.") UUID postId,
        @Schema(description = "Whether the viewer likes the post after this action.") boolean liked,
        @Schema(description = "Current like count read after the action.") int likeCount) {}
