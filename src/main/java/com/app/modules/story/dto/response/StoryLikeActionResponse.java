package com.app.modules.story.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a like or unlike action with the fresh trigger-maintained counter. */
@Schema(description = "Result of a story like or unlike action")
public record StoryLikeActionResponse(
        @Schema(description = "Affected story identifier.") UUID storyId,
        @Schema(description = "Whether the viewer likes the story after this action.")
                boolean liked,
        @Schema(description = "Current like count read after the action.") int likeCount) {}
