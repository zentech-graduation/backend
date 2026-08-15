package com.app.modules.post.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for one liked-posts list entry. */
@Schema(description = "Liked post with the like timestamp")
public record LikedPostResponse(
        @Schema(description = "The liked post.") PostResponse post,
        @Schema(description = "When the viewer liked the post.") OffsetDateTime likedAt) {}
