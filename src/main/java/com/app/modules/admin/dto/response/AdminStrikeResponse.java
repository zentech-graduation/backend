package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One strike, visible to administrators only.
 *
 * @param id strike identifier
 * @param userId account the strike is against
 * @param strikeNumber position in the account's active strike sequence, starting at 1
 * @param triggeredBy moderator whose warning tipped the account over, or null once that account is
 *     deleted
 * @param revokedAt moment the strike was revoked, or null while it stands
 * @param createdAt moment the strike was issued
 */
@Schema(description = "One strike against an account")
public record AdminStrikeResponse(
        UUID id,
        UUID userId,
        short strikeNumber,
        UUID triggeredBy,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt) {}
