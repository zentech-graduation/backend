package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A strike entry in an account's violation history.
 *
 * <p>Carries its position in the active strike sequence, which a warning has no equivalent of. It
 * carries no reason key or note: a strike is produced by the third warning rather than cited on its
 * own, and the reasons are on the warnings that produced it.
 *
 * <p>Visible to administrators only, so this shape never appears on a moderator's page.
 */
@Schema(description = "A strike entry in an account's violation history")
public record AdminStrikeViolationResponse(
        @Schema(
                        description = "Discriminator; always 'strike' on this shape",
                        allowableValues = {AdminViolationResponse.KIND_STRIKE},
                        example = AdminViolationResponse.KIND_STRIKE)
                String kind,
        @Schema(description = "Strike identifier") UUID id,
        @Schema(description = "Account the strike is against") UUID userId,
        @Schema(
                        description =
                                "Moderator whose warning tipped the account over; null once that"
                                        + " account is deleted",
                        nullable = true)
                UUID actorId,
        @Schema(description = "Position in the active strike sequence, starting at 1")
                short strikeNumber,
        @Schema(description = "Moment the strike was issued") OffsetDateTime createdAt)
        implements AdminViolationResponse {}
