package com.app.modules.post.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for replacing a post caption")
public record UpdatePostCaptionRequest(
        @Schema(
                        description =
                                "New caption (max 2200 characters); an empty string clears the"
                                        + " caption",
                        example = "Updated caption #travel",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Size(max = 2200)
                String caption) {}
