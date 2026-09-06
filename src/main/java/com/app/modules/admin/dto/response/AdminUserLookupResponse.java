package com.app.modules.admin.dto.response;

import java.util.UUID;

import com.app.common.response.UserSummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entry in a batch resolution of account identifiers to display information.
 *
 * <p>There is exactly one entry per requested identifier, in the order requested, so a client can
 * build a complete lookup map in one pass. An identifier that resolves to nothing still gets an
 * entry, because omitting it silently would leave the client's map short in a way it cannot detect
 * and it would go on rendering a bare identifier with no idea why.
 *
 * <p>Display information only. This is reachable by a moderator, and it must never become a second
 * route to the administrative account detail: no status, no role, no email, no counts.
 *
 * @param userId the identifier that was requested, echoed so the caller can key on it
 * @param found whether an account holds that identifier; false for an unknown or deleted one
 * @param user the display information, or null when {@code found} is false
 */
@Schema(description = "One resolved account identifier")
public record AdminUserLookupResponse(
        @Schema(description = "The identifier that was requested") UUID userId,
        @Schema(
                        description =
                                "Whether an account holds that identifier. False for an unknown or"
                                        + " deleted one, whose entry is still returned so the"
                                        + " caller's map is complete.")
                boolean found,
        @Schema(description = "Display information; null when found is false", nullable = true)
                UserSummaryResponse user) {}
