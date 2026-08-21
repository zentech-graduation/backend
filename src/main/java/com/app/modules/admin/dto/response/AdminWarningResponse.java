package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One warning as a moderator sees it.
 *
 * @param id warning identifier
 * @param userId account the warning is against
 * @param issuedBy moderator who issued it, or null once that account is deleted
 * @param reasonKey key of the {@code report_reason_configs} row cited
 * @param note the moderator's own description of what happened
 * @param revokedAt moment the warning was revoked, or null while it stands
 * @param createdAt moment the warning was issued
 */
@Schema(description = "One warning issued against an account")
public record AdminWarningResponse(
        UUID id,
        UUID userId,
        UUID issuedBy,
        String reasonKey,
        String note,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt) {}
