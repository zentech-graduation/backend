package com.app.modules.post.dto.request;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for replacing a post caption")
public record UpdatePostCaptionRequest(
        @Schema(
                        description = "New caption; an empty string clears the caption",
                        example = "Updated caption #travel",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                String caption) {}
