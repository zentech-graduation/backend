package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** One strike, visible to administrators only. */
@Schema(description = "One strike against an account")
public record AdminStrikeResponse(
        @Schema(description = "Strike identifier") UUID id,
        @Schema(description = "Account the strike is against") UUID userId,
        @Schema(description = "Position in the account's active strike sequence, starting at 1")
                short strikeNumber,
        @Schema(
                        description =
                                "Moderator whose warning tipped the account over; null once that"
                                        + " account is deleted",
                        nullable = true)
                UUID triggeredBy,
        @Schema(
                        description =
                                "Moment the strike was revoked; null while it stands, which is the"
                                        + " normal case",
                        nullable = true)
                OffsetDateTime revokedAt,
        @Schema(description = "Moment the strike was issued") OffsetDateTime createdAt) {}
