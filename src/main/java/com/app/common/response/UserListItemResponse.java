package com.app.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of a user list - a public summary paired with the viewer's relationship to that user.
 *
 * <p>Used wherever a list of users is rendered with a follow or block control per row: likers,
 * followers, and following. {@link UserSummaryResponse} itself stays viewer-independent so it
 * remains safe to embed in cached or broadcast payloads; relationship state travels alongside it in
 * this wrapper instead.
 */
@Schema(description = "A user list row: identity summary plus the viewer's relationship to them")
public record UserListItemResponse(
        @Schema(description = "Public identity summary.") UserSummaryResponse user,
        @Schema(description = "The viewer's relationship to this user.")
                ViewerRelationshipResponse viewerState) {}
