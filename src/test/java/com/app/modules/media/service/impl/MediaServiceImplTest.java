package com.app.modules.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.common.exception.AppException;
import com.app.common.security.user.UserPrincipal;
import com.app.common.settings.service.SystemSettingService;
import com.app.modules.media.config.MediaProperties;
import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.dto.response.MediaUploadUrlResponse;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.enums.MediaType;
import com.app.modules.media.mapper.MediaAssetMapper;
import com.app.modules.media.repository.MediaAssetRepository;
import com.app.modules.media.service.MediaEventService;
import com.app.modules.media.storage.MediaStorageKeyGenerator;
import com.app.modules.media.storage.ObjectStoragePresignService;
import com.app.modules.media.validation.MediaMetadataValidator;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000456");
    private static final String VALID_STORAGE_KEY = "users/%s/media/image.jpg".formatted(USER_ID);

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private SystemSettingService systemSettingService;
    @Mock private MediaEventService mediaEventService;
    @Mock private MediaStorageKeyGenerator storageKeyGenerator;
    @Mock private ObjectStoragePresignService objectStoragePresignService;

    private MediaServiceImpl service;

    @BeforeEach
    void setUp() {
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.setCdnBaseUrl("https://cdn.example.com/assets");
        service =
                new MediaServiceImpl(
                        mediaAssetRepository,
                        new MediaMetadataValidator(mediaProperties),
                        systemSettingService,
                        mediaEventService,
                        new MediaAssetMapper(),
                        mediaProperties,
                        storageKeyGenerator,
                        objectStoragePresignService);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new UserPrincipal(USER_ID, "user@example.com", "USER", "ACTIVE"),
                                "token",
                                java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUploadUrl_validRequest_returnsStorageKeyAndPresignedUrl() throws Exception {
        MediaUploadUrlRequest request =
                new MediaUploadUrlRequest(MediaType.IMAGE, "IMAGE/JPEG", 1024L);
        String storageKey = "users/%s/media/file.jpg".formatted(USER_ID);
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        when(storageKeyGenerator.generate(USER_ID, MediaType.IMAGE, "image/jpeg"))
                .thenReturn(storageKey);
        when(objectStoragePresignService.presignPutObject(storageKey, "image/jpeg", 1024L))
                .thenReturn(
                        new ObjectStoragePresignService.PresignedUpload(
                                new java.net.URL("https://r2.example.com/bucket/file.jpg"),
                                "PUT",
                                Map.of("content-type", "image/jpeg"),
                                java.time.Instant.parse("2026-05-27T12:00:00Z")));

        MediaUploadUrlResponse response = service.createUploadUrl(request);

        assertThat(response.storageKey()).isEqualTo(storageKey);
        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.requiredHeaders()).containsEntry("content-type", "image/jpeg");
        verify(mediaAssetRepository, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createUploadUrl_rejectsOversizedFileBeforePresigning() {
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(1L);
        MediaUploadUrlRequest request =
                new MediaUploadUrlRequest(MediaType.IMAGE, "image/jpeg", 2L * 1024L * 1024L);

        assertThatThrownBy(() -> service.createUploadUrl(request)).isInstanceOf(AppException.class);

        verify(objectStoragePresignService, never())
                .presignPutObject(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void completeUpload_persistsMediaAssetAndPublishesUploadedEvent() {
        MediaUploadCompleteRequest request = imageRequest();
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(false);
        when(mediaAssetRepository.insert(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            MediaAsset draft = invocation.getArgument(0);
                            draft.setId(MEDIA_ID);
                            draft.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                            return draft;
                        });

        MediaAssetResponse response = service.completeUpload(request);

        assertThat(response.id()).isEqualTo(MEDIA_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.cdnUrl())
                .isEqualTo("https://cdn.example.com/assets/" + VALID_STORAGE_KEY);
        verify(mediaEventService).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeUpload_duplicateStorageKeyRejectsBeforeInsertAndEvent() {
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(true);

        assertThatThrownBy(() -> service.completeUpload(imageRequest()))
                .isInstanceOf(AppException.class)
                .hasMessage("Media storage key already exists");

        verify(mediaAssetRepository, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeUpload_storageKeyOutsideCurrentUserPrefixRejectsBeforeInsertAndEvent() {
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/00000000-0000-0000-0000-000000000999/media/image.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        1024L,
                        800,
                        600,
                        null,
                        "blur");

        assertThatThrownBy(() -> service.completeUpload(request)).isInstanceOf(AppException.class);

        verify(mediaAssetRepository, never())
                .existsByStorageKey(org.mockito.ArgumentMatchers.anyString());
        verify(mediaAssetRepository, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeUpload_uniqueViolationMapsToDuplicateStorageKey() {
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(false);
        when(mediaAssetRepository.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate", new SQLException("duplicate", "23505")));

        assertThatThrownBy(() -> service.completeUpload(imageRequest()))
                .isInstanceOf(AppException.class)
                .hasMessage("Media storage key already exists");

        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeUpload_unknownIntegrityViolationIsNotMaskedAsDuplicateStorageKey() {
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(false);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException(
                        "check violation", new SQLException("check violation", "23514"));
        when(mediaAssetRepository.insert(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

        assertThatThrownBy(() -> service.completeUpload(imageRequest())).isSameAs(failure);

        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeUpload_invalidMetadataCreatesNoMediaAndNoEvent() {
        when(systemSettingService.getRequiredLong("max_media_size_mb")).thenReturn(100L);
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        VALID_STORAGE_KEY,
                        MediaType.IMAGE,
                        "image/gif",
                        1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> service.completeUpload(request)).isInstanceOf(AppException.class);

        verify(mediaAssetRepository, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    private MediaUploadCompleteRequest imageRequest() {
        return new MediaUploadCompleteRequest(
                VALID_STORAGE_KEY, MediaType.IMAGE, "image/jpeg", 1024L, 800, 600, null, "blur");
    }
}
