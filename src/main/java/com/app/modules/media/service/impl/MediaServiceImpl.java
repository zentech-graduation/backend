package com.app.modules.media.service.impl;

import java.net.URI;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.app.modules.media.mapper.MediaAssetMapper;
import com.app.modules.media.repository.MediaAssetRepository;
import com.app.modules.media.service.MediaEventService;
import com.app.modules.media.service.MediaService;
import com.app.modules.media.storage.MediaStorageKeyGenerator;
import com.app.modules.media.storage.ObjectStoragePresignService;
import com.app.modules.media.validation.MediaMetadataValidator;
import com.app.modules.media.validation.ValidatedMediaMetadata;
import com.app.modules.media.validation.ValidatedMediaUploadRequest;

@Service
public class MediaServiceImpl implements MediaService {

    private static final String MAX_MEDIA_SIZE_SETTING_KEY = "max_media_size_mb";
    private static final String STORAGE_KEY_PREFIX_TEMPLATE = "users/%s/media/";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaMetadataValidator metadataValidator;
    private final SystemSettingService systemSettingService;
    private final MediaEventService mediaEventService;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaProperties mediaProperties;
    private final MediaStorageKeyGenerator storageKeyGenerator;
    private final ObjectStoragePresignService objectStoragePresignService;

    public MediaServiceImpl(
            MediaAssetRepository mediaAssetRepository,
            MediaMetadataValidator metadataValidator,
            SystemSettingService systemSettingService,
            MediaEventService mediaEventService,
            MediaAssetMapper mediaAssetMapper,
            MediaProperties mediaProperties,
            MediaStorageKeyGenerator storageKeyGenerator,
            ObjectStoragePresignService objectStoragePresignService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.metadataValidator = metadataValidator;
        this.systemSettingService = systemSettingService;
        this.mediaEventService = mediaEventService;
        this.mediaAssetMapper = mediaAssetMapper;
        this.mediaProperties = mediaProperties;
        this.storageKeyGenerator = storageKeyGenerator;
        this.objectStoragePresignService = objectStoragePresignService;
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
    @Transactional
    public MediaAssetResponse completeUpload(MediaUploadCompleteRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        long maxMediaSizeMegabytes =
                systemSettingService.getRequiredLong(MAX_MEDIA_SIZE_SETTING_KEY);
        ValidatedMediaMetadata metadata =
                metadataValidator.validate(request, maxMediaSizeMegabytes);
        validateStorageKeyOwnership(metadata.storageKey(), currentUserId);

        if (mediaAssetRepository.existsByStorageKey(metadata.storageKey())) {
            throw new AppException(ApiErrorCode.MEDIA_STORAGE_KEY_ALREADY_EXISTS);
        }

        MediaAsset mediaAsset =
                MediaAsset.builder()
                        .userId(currentUserId)
                        .storageKey(metadata.storageKey())
                        .cdnUrl(buildCdnUrl(metadata.storageKey()))
                        .mediaType(metadata.mediaType())
                        .mimeType(metadata.mimeType())
                        .fileSize(metadata.fileSize())
                        .width(metadata.width())
                        .height(metadata.height())
                        .duration(metadata.duration())
                        .blurhash(metadata.blurhash())
                        .build();

        try {
            MediaAsset saved = mediaAssetRepository.insert(mediaAsset);
            mediaEventService.publishMediaUploaded(saved);
            return mediaAssetMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw new AppException(ApiErrorCode.MEDIA_STORAGE_KEY_ALREADY_EXISTS);
            }
            throw ex;
        }
    }

    private static void validateStorageKeyOwnership(String storageKey, UUID userId) {
        String expectedPrefix = STORAGE_KEY_PREFIX_TEMPLATE.formatted(userId);
        if (!storageKey.startsWith(expectedPrefix)) {
            throw new AppException(ApiErrorCode.MEDIA_INVALID_METADATA);
        }
    }

    private static boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
