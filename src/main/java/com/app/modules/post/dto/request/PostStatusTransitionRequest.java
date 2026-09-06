package com.app.modules.post.dto.request;

import jakarta.validation.constraints.NotNull;

import com.app.modules.post.enums.PostStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for transitioning a post lifecycle status")
public record PostStatusTransitionRequest(
        @Schema(
                        description =
                                "Target status; valid transitions are draft to published,"
                                        + " published to archived, archived to published, and any"
                                        + " status to removed",
                        example = "archived",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                PostStatus targetStatus) {}
