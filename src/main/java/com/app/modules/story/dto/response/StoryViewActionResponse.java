package com.app.modules.story.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for a view-recording action with the fresh trigger-maintained counter. */
@Schema(description = "Result of recording a story view")
public record StoryViewActionResponse(
        @Schema(description = "Affected story identifier.") UUID storyId,
        @Schema(
                        description =
                                "Whether this call inserted a new view row; false for the owner and"
                                        + " for repeat views.")
                boolean viewed,
        @Schema(description = "Current unique-viewer count read after the action.")
                int viewCount) {}
