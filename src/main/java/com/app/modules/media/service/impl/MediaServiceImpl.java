package com.app.modules.media.service.impl;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.util.SecurityUtils;
import com.app.common.settings.service.SystemSettingService;
import com.app.modules.media.config.MediaProperties;
import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.dto.response.MediaUploadUrlResponse;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.service.MediaService;
import com.app.modules.media.storage.MediaStorageKeyGenerator;
import com.app.modules.media.storage.ObjectStorageMetadataService;
import com.app.modules.media.storage.ObjectStoragePresignService;
import com.app.modules.media.validation.MediaMetadataValidator;
import com.app.modules.media.validation.ValidatedMediaMetadata;
import com.app.modules.media.validation.ValidatedMediaUploadRequest;

@Service
public class MediaServiceImpl implements MediaService {

    private static final String MAX_MEDIA_SIZE_SETTING_KEY = "max_media_size_mb";
    private static final String STORAGE_KEY_PREFIX_TEMPLATE = "users/%s/media/";

    private final MediaMetadataValidator metadataValidator;
    private final SystemSettingService systemSettingService;
    private final MediaProperties mediaProperties;
    private final MediaStorageKeyGenerator storageKeyGenerator;
    private final ObjectStoragePresignService objectStoragePresignService;
    private final ObjectStorageMetadataService objectStorageMetadataService;
    private final MediaAssetRegistrar mediaAssetRegistrar;

    public MediaServiceImpl(
            MediaMetadataValidator metadataValidator,
            SystemSettingService systemSettingService,
            MediaProperties mediaProperties,
            MediaStorageKeyGenerator storageKeyGenerator,
            ObjectStoragePresignService objectStoragePresignService,
            ObjectStorageMetadataService objectStorageMetadataService,
            MediaAssetRegistrar mediaAssetRegistrar) {
        this.metadataValidator = metadataValidator;
        this.systemSettingService = systemSettingService;
        this.mediaProperties = mediaProperties;
        this.storageKeyGenerator = storageKeyGenerator;
        this.objectStoragePresignService = objectStoragePresignService;
        this.objectStorageMetadataService = objectStorageMetadataService;
        this.mediaAssetRegistrar = mediaAssetRegistrar;
    }

    @Override
    public MediaUploadUrlResponse createUploadUrl(MediaUploadUrlRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        long maxMediaSizeMegabytes =
                systemSettingService.getRequiredLong(MAX_MEDIA_SIZE_SETTING_KEY);
        ValidatedMediaUploadRequest metadata =
                metadataValidator.validateUploadUrlRequest(request, maxMediaSizeMegabytes);
        String storageKey =
                storageKeyGenerator.generate(
                        currentUserId, metadata.mediaType(), metadata.mimeType());
        ObjectStoragePresignService.PresignedUpload presignedUpload =
                objectStoragePresignService.presignPutObject(
                        storageKey, metadata.mimeType(), metadata.fileSize());
        return new MediaUploadUrlResponse(
                storageKey,
                presignedUpload.uploadUrl(),
                presignedUpload.method(),
                presignedUpload.requiredHeaders(),
                presignedUpload.expiresAt());
    }

    @Override
    public MediaAssetResponse completeUpload(MediaUploadCompleteRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        long maxMediaSizeMegabytes =
                systemSettingService.getRequiredLong(MAX_MEDIA_SIZE_SETTING_KEY);
        ValidatedMediaMetadata metadata =
                metadataValidator.validate(request, maxMediaSizeMegabytes);
        validateStorageKeyOwnership(metadata.storageKey(), currentUserId);
        String cdnUrl = buildCdnUrl(metadata.storageKey());

        // Deliberately outside the write transaction. A head-object is a network round trip, and
        // holding a pooled connection across it would let an R2 latency spike exhaust the pool at
        // DB_POOL_MAX concurrent uploads, stalling every request in the application rather than
        // only media ones. The cost is that a duplicate storage key now pays for a probe before
        // the duplicate check rejects it, which is an edge case, not the hot path.
        verifyUploadedObjectMatches(metadata);

        MediaAsset mediaAsset =
                MediaAsset.builder()
                        .userId(currentUserId)
                        .storageKey(metadata.storageKey())
                        .cdnUrl(cdnUrl)
                        .mediaType(metadata.mediaType())
                        .mimeType(metadata.mimeType())
                        .fileSize(metadata.fileSize())
                        .width(metadata.width())
                        .height(metadata.height())
                        .duration(metadata.duration())
                        .blurhash(metadata.blurhash())
                        .build();

        return mediaAssetRegistrar.register(mediaAsset);
    }

    /**
     * Rejects the registration unless storage already holds an object matching the submitted
     * metadata.
     *
     * <p>Fails closed: a storage outage propagates as a retryable 503 rather than allowing the row.
     * A media_assets row is permanent and every reader treats its existence as proof the object is
     * live, so a phantom row is undetectable afterwards while a rejected upload can be retried.
     */
    private void verifyUploadedObjectMatches(ValidatedMediaMetadata metadata) {
        ObjectStorageMetadataService.StoredObjectMetadata storedObject =
                objectStorageMetadataService
                        .findObjectMetadata(metadata.storageKey())
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.MEDIA_OBJECT_NOT_UPLOADED));

        if (storedObject.contentLength() != metadata.fileSize()) {
            throw new AppException(
                    ApiErrorCode.MEDIA_OBJECT_METADATA_MISMATCH,
                    "Uploaded object size does not match the submitted file size");
        }
        if (!baseMimeType(storedObject.contentType()).equals(metadata.mimeType())) {
            throw new AppException(
                    ApiErrorCode.MEDIA_OBJECT_METADATA_MISMATCH,
                    "Uploaded object content type does not match the submitted MIME type");
        }
    }

    /**
     * Reduces a stored Content-Type to a bare type/subtype for comparison against validated
     * metadata, which is already trimmed and lower-cased.
     */
    private static String baseMimeType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterStart = contentType.indexOf(';');
        String bare = parameterStart < 0 ? contentType : contentType.substring(0, parameterStart);
        return bare.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateStorageKeyOwnership(String storageKey, UUID userId) {
        String expectedPrefix = STORAGE_KEY_PREFIX_TEMPLATE.formatted(userId);
        if (!storageKey.startsWith(expectedPrefix)) {
            throw new AppException(ApiErrorCode.MEDIA_INVALID_METADATA);
        }
    }

    private String buildCdnUrl(String storageKey) {
        String cdnBaseUrl = mediaProperties.getCdnBaseUrl();
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            throw new AppException(ApiErrorCode.MEDIA_CDN_NOT_CONFIGURED);
        }
        try {
            URI baseUri = URI.create(cdnBaseUrl.trim());
            String[] pathSegments = storageKey.split("/");
            return UriComponentsBuilder.fromUri(baseUri)
                    .pathSegment(pathSegments)
                    .build()
                    .toUriString();
        } catch (IllegalArgumentException ex) {
            throw new AppException(ApiErrorCode.MEDIA_CDN_NOT_CONFIGURED);
        }
    }
}
