package com.app.modules.post.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload for creating a new post")
public record CreatePostRequest(
        @Schema(
                        description =
                                "Post caption (max 2200 characters); #tokens are extracted as"
                                        + " hashtags at publish time",
                        example = "Sunset at the beach #sunset #beach")
                @Size(max = 2200)
                String caption,
        @Schema(
                        description = "Post type",
                        example = "image",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                PostType postType,
        @Schema(
                        description =
                                "Media asset ids owned by the author, in carousel order."
                                        + " Required for image, video, and carousel posts;"
                                        + " must be null or empty for text posts.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                @Size(max = 10)
                List<UUID> mediaIds,
        @Schema(
                        description =
                                "Initial status; only draft or published are accepted and the"
                                        + " default is published",
                        example = "published")
                PostStatus status,
        @Schema(description = "Free-form location label", example = "Da Nang, Vietnam")
                @Size(max = 255)
                String locationName,
        @Schema(description = "Latitude in decimal degrees", example = "16.054407")
                @DecimalMin("-90")
                @DecimalMax("90")
                BigDecimal latitude,
        @Schema(description = "Longitude in decimal degrees", example = "108.202164")
                @DecimalMin("-180")
                @DecimalMax("180")
                BigDecimal longitude) {}
