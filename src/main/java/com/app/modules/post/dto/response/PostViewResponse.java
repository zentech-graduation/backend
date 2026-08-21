package com.app.modules.post.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API response for a recorded post view.
 *
 * <p>{@code recorded} is false only when the viewer is the post owner, since a self-view is
 * accepted but not counted. The response never reflects {@code posts.view_count} directly: that
 * counter is updated later by a background job from the underlying behavioral event, not
 * synchronously by this endpoint.
 */
@Schema(description = "Result of a post view recording")
public record PostViewResponse(
        @Schema(description = "Viewed post identifier.") UUID postId,
        @Schema(description = "Whether the view was counted (false for the post owner's own view).")
                boolean recorded) {}
