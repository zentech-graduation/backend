package com.app.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** One entry in the most-used active hashtag list. */
@Schema(description = "An active hashtag and how many published posts carry it")
public record TopHashtagResponse(
        @Schema(description = "Hashtag name without the # prefix", example = "spring") String name,
        @Schema(description = "Trigger-maintained count of published posts", example = "142")
                int postCount) {}
