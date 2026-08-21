package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A warning entry in an account's violation history.
 *
 * <p>Carries the cited reason and the moderator's note, neither of which a strike has. It carries
 * no strike number for the same reason: a warning has no position in the strike sequence.
 */
@Schema(description = "A warning entry in an account's violation history")
public record AdminWarningViolationResponse(
        @Schema(
                        description = "Discriminator; always 'warning' on this shape",
                        allowableValues = {AdminViolationResponse.KIND_WARNING},
                        example = AdminViolationResponse.KIND_WARNING)
                String kind,
        @Schema(description = "Warning identifier") UUID id,
        @Schema(description = "Account the warning is against") UUID userId,
        @Schema(
                        description =
                                "Moderator who issued the warning; null once that account is"
                                        + " deleted",
                        nullable = true)
                UUID actorId,
        @Schema(description = "Key of the report_reason_configs row cited", example = "spam")
                String reasonKey,
        @Schema(description = "The moderator's own description of what happened") String note,
        @Schema(description = "Moment the warning was issued") OffsetDateTime createdAt)
        implements AdminViolationResponse {}
