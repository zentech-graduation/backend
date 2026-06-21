package com.app.modules.post.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for one saved-posts list entry. */
@Schema(description = "Saved post with the save timestamp")
public record SavedPostResponse(
        @Schema(description = "The saved post.") PostResponse post,
        @Schema(description = "When the viewer saved the post.") OffsetDateTime savedAt) {}
