package com.app.modules.media.validation;

import com.app.modules.media.enums.MediaType;

public record ValidatedMediaMetadata(
        String storageKey,
        MediaType mediaType,
        String mimeType,
        long fileSize,
        int width,
        int height,
        Integer duration,
        String blurhash) {}
