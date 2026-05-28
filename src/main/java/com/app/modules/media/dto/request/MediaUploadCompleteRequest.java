package com.app.modules.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.app.modules.media.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;

public record MediaUploadCompleteRequest(
        @Schema(description = "Object storage key returned by the upload URL endpoint.")
                @NotBlank
                @Size(max = 512)
                String storageKey,
        @Schema(description = "Uploaded media category.", example = "IMAGE") @NotNull
                MediaType mediaType,
        @Schema(description = "Uploaded object MIME type.", example = "image/jpeg")
                @NotBlank
                @Size(max = 100)
                String mimeType,
        @Schema(description = "Uploaded object size in bytes.", example = "1048576") @Positive
                long fileSize,
        @Schema(description = "Media width in pixels.", example = "1080") @NotNull @Positive
                Integer width,
        @Schema(description = "Media height in pixels.", example = "1080") @NotNull @Positive
                Integer height,
        @Schema(description = "Video duration in seconds. Must be null for images.", example = "12")
                @PositiveOrZero
                Integer duration,
        @Schema(
                        description = "Optional client-generated blurhash preview.",
                        example = "LKO2?U%2Tw=w]~RBVZRi};RPxuwH")
                @Size(max = 100)
                String blurhash) {}
