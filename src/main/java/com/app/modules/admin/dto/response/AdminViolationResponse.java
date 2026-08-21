package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entry in an account's violation history, discriminated by {@code kind}.
 *
 * <p>Warnings and strikes are interleaved in one chronological listing rather than returned as two
 * lists, because a reviewer reads them as a sequence: which warnings preceded which strike is the
 * whole question. A moderator receives warnings only, so the {@code strikeNumber} field is null on
 * every row it sees.
 *
 * @param kind {@code warning} or {@code strike}
 * @param id identifier of the underlying row
 * @param userId account the entry is against
 * @param actorId moderator who issued the warning, or whose warning triggered the strike; null once
 *     that account is deleted
 * @param reasonKey key of the {@code report_reason_configs} row cited; null on a strike
 * @param note the moderator's own description; null on a strike
 * @param strikeNumber position in the active strike sequence; null on a warning
 * @param createdAt moment the entry was recorded
 */
@Schema(description = "One entry in an account's violation history")
public record AdminViolationResponse(
        String kind,
        UUID id,
        UUID userId,
        UUID actorId,
        String reasonKey,
        String note,
        Short strikeNumber,
        OffsetDateTime createdAt) {

    public static final String KIND_WARNING = "warning";
    public static final String KIND_STRIKE = "strike";
}
