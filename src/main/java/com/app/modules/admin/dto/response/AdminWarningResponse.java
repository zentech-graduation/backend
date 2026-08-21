package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** One warning as a moderator sees it. */
@Schema(description = "One warning issued against an account")
public record AdminWarningResponse(
        @Schema(description = "Warning identifier") UUID id,
        @Schema(description = "Account the warning is against") UUID userId,
        @Schema(
                        description = "Moderator who issued it; null once that account is deleted",
                        nullable = true)
                UUID issuedBy,
        @Schema(description = "Key of the report_reason_configs row cited", example = "spam")
                String reasonKey,
        @Schema(description = "The moderator's own description of what happened") String note,
        @Schema(
                        description =
                                "Moment the warning was revoked; null while it stands, which is"
                                        + " the normal case",
                        nullable = true)
                OffsetDateTime revokedAt,
        @Schema(description = "Moment the warning was issued") OffsetDateTime createdAt) {}
