package com.app.modules.comment.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for creating a comment or a reply")
public record CreateCommentRequest(
        @Schema(
                        description = "Target post identifier; must match the path post id.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID postId,
        @Schema(
                        description =
                                "Parent comment identifier for a reply; null for a top-level comment.",
                        nullable = true)
                UUID parentId,
        @Schema(
                        description = "Comment body.",
                        example = "Great shot! @alex",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2200)
                String content) {}
