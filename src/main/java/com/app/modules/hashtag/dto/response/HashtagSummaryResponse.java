package com.app.modules.hashtag.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** A hashtag reduced to what a post response needs to render it as a link. */
@Schema(description = "Hashtag associated with a post")
public record HashtagSummaryResponse(
        @Schema(
                        description = "Unique hashtag identifier",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                UUID id,
        @Schema(
                        description = "Hashtag name without the # prefix, always lowercase",
                        example = "spring")
                String name) {}
