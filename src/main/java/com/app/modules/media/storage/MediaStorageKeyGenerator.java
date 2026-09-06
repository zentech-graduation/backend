package com.app.modules.media.storage;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.enums.MediaType;

@Component
public class MediaStorageKeyGenerator {

    public String generate(UUID userId, MediaType mediaType, String mimeType) {
        if (userId == null || mediaType == null || mimeType == null) {
            throw new AppException(ApiErrorCode.MEDIA_INVALID_METADATA);
        }
        return "users/%s/media/%s.%s".formatted(userId, UUID.randomUUID(), extensionFor(mimeType));
    }

    // Every type in app.media.allowed-image-mime-types and app.media.allowed-video-mime-types
    // needs an entry here. A type allowed by configuration but missing below reaches the default
    // branch and fails every presign for that type, so the two lists must be widened together.
    private static String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            default -> throw new AppException(ApiErrorCode.MEDIA_INVALID_METADATA);
        };
    }
}
