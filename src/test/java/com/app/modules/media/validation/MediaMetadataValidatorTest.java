package com.app.modules.media.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;
import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.enums.MediaType;

class MediaMetadataValidatorTest {

    private MediaMetadataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MediaMetadataValidator(new MediaProperties());
    }

    @Test
    void validateImage_acceptsAllowedMetadataAndNormalizesValues() {
        ValidatedMediaMetadata metadata =
                validator.validate(
                        new MediaUploadCompleteRequest(
                                " users/123/media/image.jpg ",
                                MediaType.IMAGE,
                                " IMAGE/JPEG ",
                                1024L,
                                800,
                                600,
                                null,
                                " blur "),
                        100);

        assertThat(metadata.storageKey()).isEqualTo("users/123/media/image.jpg");
        assertThat(metadata.mimeType()).isEqualTo("image/jpeg");
        assertThat(metadata.duration()).isNull();
        assertThat(metadata.blurhash()).isEqualTo("blur");
    }

    @Test
    void validateVideo_requiresDuration() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/video.mp4",
                        MediaType.VIDEO,
                        "video/mp4",
                        1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Video duration is required");
    }

    @Test
    void validateImageRejectsDuration() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        1024L,
                        800,
                        600,
                        1,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Image duration must be null");
    }

    @Test
    void validateRejectsUnsupportedMimeType() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.gif",
                        MediaType.IMAGE,
                        "image/gif",
                        1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Media MIME type is not allowed");
    }

    @Test
    void validateRejectsStorageTraversalAndBackslash() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/../secret.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Storage key format is invalid");
    }

    @Test
    void validateRejectsFileLargerThanSystemSetting() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        2L * 1024L * 1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 1))
                .isInstanceOf(AppException.class)
                .hasMessage("File size exceeds the configured limit");
    }

    @Test
    void validateUploadUrlRequest_acceptsAllowedMimeTypeAndSize() {
        ValidatedMediaUploadRequest request =
                validator.validateUploadUrlRequest(
                        new MediaUploadUrlRequest(MediaType.VIDEO, " VIDEO/MP4 ", 1024L), 100);

        assertThat(request.mediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(request.mimeType()).isEqualTo("video/mp4");
        assertThat(request.fileSize()).isEqualTo(1024L);
    }

    @Test
    void validateUploadUrlRequest_rejectsOversizedFile() {
        MediaUploadUrlRequest request =
                new MediaUploadUrlRequest(MediaType.IMAGE, "image/jpeg", 2L * 1024L * 1024L);

        assertThatThrownBy(() -> validator.validateUploadUrlRequest(request, 1))
                .isInstanceOf(AppException.class)
                .hasMessage("File size exceeds the configured limit");
    }
}
