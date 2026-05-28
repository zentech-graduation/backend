package com.app.modules.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.app.modules.media.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;

public record MediaUploadUrlRequest(
        @Schema(description = "Media category being uploaded.", example = "IMAGE") @NotNull
                MediaType mediaType,
        @Schema(
                        description = "Client-selected MIME type to sign into the upload request.",
                        example = "image/jpeg")
                @NotBlank
                @Size(max = 100)
                String mimeType,
        @Schema(description = "Client-selected file size in bytes.", example = "1048576") @Positive
                long fileSize) {}
