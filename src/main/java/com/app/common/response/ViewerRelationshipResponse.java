package com.app.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The requesting viewer's relationship to another user, embedded wherever a response renders a
 * follow or block control for that user.
 *
 * <p>All five fields are always present and never null; an anonymous viewer or a self-reference
 * resolves to every field {@code false} rather than the field being omitted, so a client never has
 * to branch on presence. {@code isFollowing} and {@code isFollowRequested} are mutually exclusive:
 * the {@code follows} row for a pair is either {@code accepted} or {@code pending}, never both.
 */
@Schema(description = "The viewer's relationship to another user")
public record ViewerRelationshipResponse(
        @Schema(description = "Viewer has an accepted follow of this user", example = "false")
                boolean isFollowing,
        @Schema(description = "Viewer has a pending follow request to this user", example = "false")
                boolean isFollowRequested,
        @Schema(description = "This user has an accepted follow of the viewer", example = "false")
                boolean isFollowedBy,
        @Schema(description = "Viewer has blocked this user", example = "false") boolean isBlocking,
        @Schema(description = "This user has blocked the viewer", example = "false")
                boolean isBlockedBy) {

    /** Shared all-false instance for an anonymous viewer or a self-reference. */
    public static final ViewerRelationshipResponse NONE =
            new ViewerRelationshipResponse(false, false, false, false, false);
}
