package com.app.modules.hashtag.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public representation of a hashtag returned by lookup and search endpoints. */
@Schema(description = "A hashtag with its current usage counter")
public record HashtagResponse(
        @Schema(
                        description = "Unique hashtag identifier",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                UUID id,
        @Schema(
                        description = "Hashtag name without the # prefix, always lowercase",
                        example = "spring")
                String name,
        @Schema(
                        description =
                                "Number of published posts currently tagged with this hashtag",
                        example = "142")
                int postCount,
        @Schema(
                        description = "UTC timestamp when the hashtag was first used",
                        example = "2024-01-15T10:30:00Z")
                OffsetDateTime createdAt) {}
