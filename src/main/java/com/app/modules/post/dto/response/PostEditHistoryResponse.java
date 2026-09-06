package com.app.modules.post.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.common.response.UserSummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** API response for one append-only caption edit audit row. */
@Schema(description = "Caption edit history entry")
public record PostEditHistoryResponse(
        @Schema(description = "History row identifier.") UUID id,
        @Schema(description = "Edited post identifier.") UUID postId,
        @Schema(description = "User who performed the edit.") UserSummaryResponse editor,
        @Schema(
                        description =
                                "Caption value before the edit; null when the post had no caption.",
                        nullable = true)
                String previousCaption,
        @Schema(description = "Edit timestamp.") OffsetDateTime editedAt) {}
