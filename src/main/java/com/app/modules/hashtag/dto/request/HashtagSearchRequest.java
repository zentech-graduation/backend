package com.app.modules.hashtag.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Query parameters for cursor-paginated hashtag search. */
@Schema(description = "Cursor-paginated hashtag search query")
public record HashtagSearchRequest(
        @Schema(
                        description = "Search term; 1–100 characters",
                        example = "spring",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(min = 1, max = 100)
                String q,
        @Schema(
                        description =
                                "Opaque cursor from the previous page; omit for the first page",
                        example = "MjA=")
                String cursor,
        @Schema(description = "Maximum results per page; defaults to 20", example = "20")
                @Min(1)
                @Max(100)
                Integer limit) {}
