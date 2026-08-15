package com.app.modules.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.app.common.exception.AppException;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.enums.MediaType;
import com.app.modules.media.mapper.MediaAssetMapper;
import com.app.modules.media.repository.MediaAssetRepository;
import com.app.modules.media.service.MediaEventService;

@ExtendWith(MockitoExtension.class)
class MediaAssetRegistrarTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000456");
    private static final String VALID_STORAGE_KEY = "users/%s/media/image.jpg".formatted(USER_ID);

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaEventService mediaEventService;

    private MediaAssetRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar =
                new MediaAssetRegistrar(
                        mediaAssetRepository, mediaEventService, new MediaAssetMapper());
    }

    @Test
    void register_persistsMediaAssetAndPublishesUploadedEvent() {
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(false);
        when(mediaAssetRepository.insert(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            MediaAsset draft = invocation.getArgument(0);
                            draft.setId(MEDIA_ID);
                            draft.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                            return draft;
                        });

        MediaAssetResponse response = registrar.register(draft());

        assertThat(response.id()).isEqualTo(MEDIA_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        verify(mediaEventService).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_duplicateStorageKeyRejectsBeforeInsertAndEvent() {
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(true);

        assertThatThrownBy(() -> registrar.register(draft()))
                .isInstanceOf(AppException.class)
                .hasMessage("Media storage key already exists");

        verify(mediaAssetRepository, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_uniqueViolationMapsToDuplicateStorageKey() {
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(false);
        when(mediaAssetRepository.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate", new SQLException("duplicate", "23505")));

        assertThatThrownBy(() -> registrar.register(draft()))
                .isInstanceOf(AppException.class)
                .hasMessage("Media storage key already exists");

        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void register_unknownIntegrityViolationIsNotMaskedAsDuplicateStorageKey() {
        when(mediaAssetRepository.existsByStorageKey(VALID_STORAGE_KEY)).thenReturn(false);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException(
                        "check violation", new SQLException("check violation", "23514"));
        when(mediaAssetRepository.insert(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

        assertThatThrownBy(() -> registrar.register(draft())).isSameAs(failure);

        verify(mediaEventService, never()).publishMediaUploaded(org.mockito.ArgumentMatchers.any());
    }

    private MediaAsset draft() {
        return MediaAsset.builder()
                .userId(USER_ID)
                .storageKey(VALID_STORAGE_KEY)
                .cdnUrl("https://cdn.example.com/assets/" + VALID_STORAGE_KEY)
                .mediaType(MediaType.IMAGE)
                .mimeType("image/jpeg")
                .fileSize(1024L)
                .width(800)
                .height(600)
                .duration(null)
                .blurhash("blur")
                .build();
    }
}
