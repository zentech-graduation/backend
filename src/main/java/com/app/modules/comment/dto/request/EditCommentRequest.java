package com.app.modules.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for editing a comment body")
public record EditCommentRequest(
        @Schema(
                        description = "New comment body.",
                        example = "Edited: great shot!",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2200)
                String content) {}
