package com.app.modules.media.validation;

import java.util.Collections;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;
import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.enums.MediaType;

@Component
public class MediaMetadataValidator {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final MediaProperties mediaProperties;

    public MediaMetadataValidator(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    public ValidatedMediaMetadata validate(
            MediaUploadCompleteRequest request, long maxMediaSizeMegabytes) {
        String storageKey = normalizeStorageKey(request.storageKey());
        MediaType mediaType = request.mediaType();
        String mimeType = normalizeMimeType(request.mimeType());
        long maxBytes = maxMediaSizeBytes(maxMediaSizeMegabytes);

        validateStorageKey(storageKey);
        validateMimeType(mediaType, mimeType);
        validateFileSize(request.fileSize(), maxBytes);
        validateDimensions(request.width(), request.height());
        validateDuration(mediaType, request.duration());

        return new ValidatedMediaMetadata(
                storageKey,
                mediaType,
                mimeType,
                request.fileSize(),
                request.width(),
                request.height(),
                request.duration(),
                normalizeOptionalText(request.blurhash()));
    }

    public ValidatedMediaUploadRequest validateUploadUrlRequest(
            MediaUploadUrlRequest request, long maxMediaSizeMegabytes) {
        MediaType mediaType = request.mediaType();
        String mimeType = normalizeMimeType(request.mimeType());
        long maxBytes = maxMediaSizeBytes(maxMediaSizeMegabytes);

        validateMimeType(mediaType, mimeType);
        validateFileSize(request.fileSize(), maxBytes);

        return new ValidatedMediaUploadRequest(mediaType, mimeType, request.fileSize());
    }

    private void validateStorageKey(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            reject("Storage key is required");
        }
        if (storageKey.contains("..")
                || storageKey.contains("\\")
                || storageKey.contains("//")
                || storageKey.startsWith("/")
                || storageKey.endsWith("/")) {
            reject("Storage key format is invalid");
        }
        Pattern pattern = Pattern.compile(mediaProperties.getStorageKeyPattern());
        if (!pattern.matcher(storageKey).matches()) {
            reject("Storage key format is invalid");
        }
    }

    /**
     * The MIME types the upload path accepts for a media type, normalized and sorted.
     *
     * <p>This is the accessor the constraints endpoint publishes from. Both the rejection message
     * and the endpoint read it, so neither can describe an allowlist the rule does not enforce.
     *
     * @param mediaType the media type whose allowlist is wanted
     * @return the accepted MIME types, lower-cased and in ascending order
     */
    public SortedSet<String> acceptedMimeTypes(MediaType mediaType) {
        return normalizedAllowed(
                mediaType == MediaType.IMAGE
                        ? mediaProperties.getAllowedImageMimeTypes()
                        : mediaProperties.getAllowedVideoMimeTypes());
    }

    private void validateMimeType(MediaType mediaType, String mimeType) {
        if (mediaType == null) {
            reject("Media type is required");
        }
        SortedSet<String> allowed = acceptedMimeTypes(mediaType);
        if (!allowed.contains(mimeType)) {
            // Naming the accepted set is what stops a client keeping its own copy of the list and
            // drifting from it. Rendered from the same set the check above consults, so the
            // message cannot disagree with the rule it describes.
            reject(
                    "Unsupported %s MIME type '%s'. Accepted: %s"
                            .formatted(
                                    mediaType.name().toLowerCase(Locale.ROOT),
                                    mimeType,
                                    String.join(", ", allowed)));
        }
    }

    private void validateFileSize(long fileSize, long maxBytes) {
        if (fileSize <= 0) {
            reject("File size must be positive");
        }
        if (fileSize > maxBytes) {
            reject("File size exceeds the configured limit");
        }
    }

    private long maxMediaSizeBytes(long maxMediaSizeMegabytes) {
        if (maxMediaSizeMegabytes <= 0) {
            throw new AppException(
                    ApiErrorCode.SERVICE_UNAVAILABLE, "Media size system setting must be positive");
        }
        try {
            return Math.multiplyExact(maxMediaSizeMegabytes, BYTES_PER_MEGABYTE);
        } catch (ArithmeticException ex) {
            throw new AppException(
                    ApiErrorCode.SERVICE_UNAVAILABLE, "Media size system setting is too large");
        }
    }

    private void validateDimensions(Integer width, Integer height) {
        if (width == null || width <= 0 || height == null || height <= 0) {
            reject("Media width and height must be positive");
        }
    }

    private void validateDuration(MediaType mediaType, Integer duration) {
        if (mediaType == MediaType.IMAGE && duration != null) {
            reject("Image duration must be null");
        }
        if (mediaType == MediaType.VIDEO && duration == null) {
            reject("Video duration is required");
        }
        if (duration != null && duration < 0) {
            reject("Duration must be non-negative");
        }
    }

    // Sorted rather than hashed so the rejection message and the constraints endpoint list the
    // types in one stable order regardless of how the configuration happens to order them.
    private SortedSet<String> normalizedAllowed(java.util.List<String> values) {
        return Collections.unmodifiableSortedSet(
                values.stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalizeMimeType)
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
    }

    private String normalizeStorageKey(String storageKey) {
        return storageKey == null ? null : storageKey.trim();
    }

    private String normalizeMimeType(String mimeType) {
        return mimeType == null ? null : mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void reject(String message) {
        throw new AppException(ApiErrorCode.MEDIA_INVALID_METADATA, message);
    }
}
