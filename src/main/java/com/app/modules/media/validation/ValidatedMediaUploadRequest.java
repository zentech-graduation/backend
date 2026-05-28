package com.app.modules.media.validation;

import com.app.modules.media.enums.MediaType;

public record ValidatedMediaUploadRequest(MediaType mediaType, String mimeType, long fileSize) {}
