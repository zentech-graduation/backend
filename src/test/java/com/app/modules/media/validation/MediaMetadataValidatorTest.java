package com.app.modules.media.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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

    // The unsupported example used to be image/gif. GIF is an accepted image type now, so this
    // pins HEIC instead, which is deliberately refused because the CDN serves what was stored and
    // Chrome and Firefox cannot render it.
    @Test
    void validateRejectsUnsupportedMimeType() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.heic",
                        MediaType.IMAGE,
                        "image/heic",
                        1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage(
                        "Unsupported image MIME type 'image/heic'. Accepted: image/gif,"
                                + " image/jpeg, image/png, image/webp");
    }

    @Test
    void validateUploadUrlRequest_gifImage_isAccepted() {
        ValidatedMediaUploadRequest request =
                validator.validateUploadUrlRequest(
                        new MediaUploadUrlRequest(MediaType.IMAGE, "image/gif", 1024L), 100);

        assertThat(request.mimeType()).isEqualTo("image/gif");
    }

    @Test
    void validateUploadUrlRequest_quicktimeVideo_isAccepted() {
        ValidatedMediaUploadRequest request =
                validator.validateUploadUrlRequest(
                        new MediaUploadUrlRequest(MediaType.VIDEO, "video/quicktime", 1024L), 100);

        assertThat(request.mimeType()).isEqualTo("video/quicktime");
    }

    @Test
    void validateUploadUrlRequest_heifImage_isRejected() {
        MediaUploadUrlRequest request =
                new MediaUploadUrlRequest(MediaType.IMAGE, "image/heif", 1024L);

        assertThatThrownBy(() -> validator.validateUploadUrlRequest(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Unsupported image MIME type 'image/heif'");
    }

    // The point of the message is that a client never needs its own copy of the list. Driving the
    // validator from a non-default configuration proves the message is rendered from the same
    // property the rule reads, rather than from a second literal that could drift away from it.
    @Test
    void validate_rejectionMessageIsRenderedFromTheConfiguredAllowlist() {
        MediaProperties properties = new MediaProperties();
        properties.setAllowedImageMimeTypes(List.of("image/png", "image/avif"));
        MediaMetadataValidator configuredValidator = new MediaMetadataValidator(properties);
        MediaUploadUrlRequest request =
                new MediaUploadUrlRequest(MediaType.IMAGE, "image/jpeg", 1024L);

        assertThatThrownBy(() -> configuredValidator.validateUploadUrlRequest(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage(
                        "Unsupported image MIME type 'image/jpeg'. Accepted: image/avif,"
                                + " image/png");
    }

    @Test
    void validate_videoRejectionMessageNamesTheAcceptedVideoTypes() {
        MediaUploadUrlRequest request =
                new MediaUploadUrlRequest(MediaType.VIDEO, "video/x-msvideo", 1024L);

        assertThatThrownBy(() -> validator.validateUploadUrlRequest(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage(
                        "Unsupported video MIME type 'video/x-msvideo'. Accepted: video/mp4,"
                                + " video/quicktime, video/webm");
    }

    @Test
    void validate_videoDurationOverConfiguredMaximum_isRejected() {
        MediaUploadCompleteRequest request = videoRequestWithDuration(181);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Video duration must not exceed 180 seconds");
    }

    @Test
    void validate_videoDurationExactlyAtConfiguredMaximum_isAccepted() {
        ValidatedMediaMetadata metadata = validator.validate(videoRequestWithDuration(180), 100);

        assertThat(metadata.duration()).isEqualTo(180);
    }

    // Proves the ceiling is read from configuration rather than compiled in, which is what lets
    // the constraints endpoint publish it without a second literal to keep in step.
    @Test
    void validate_videoDurationLimitIsReadFromConfiguration() {
        MediaProperties properties = new MediaProperties();
        properties.setMaxVideoDurationSeconds(30);
        MediaMetadataValidator configuredValidator = new MediaMetadataValidator(properties);

        assertThatThrownBy(() -> configuredValidator.validate(videoRequestWithDuration(31), 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Video duration must not exceed 30 seconds");
    }

    private static MediaUploadCompleteRequest videoRequestWithDuration(int duration) {
        return new MediaUploadCompleteRequest(
                "users/123/media/video.mp4",
                MediaType.VIDEO,
                "video/mp4",
                1024L,
                800,
                600,
                duration,
                null);
    }

    // The accepted set the endpoint publishes must be the same object the rule consults, so this
    // pins the accessor rather than letting the endpoint build its own copy of the list.
    @Test
    void acceptedMimeTypes_returnsTheConfiguredAllowlistNormalizedAndSorted() {
        MediaProperties properties = new MediaProperties();
        properties.setAllowedImageMimeTypes(List.of(" IMAGE/WEBP ", "image/png", ""));
        MediaMetadataValidator configuredValidator = new MediaMetadataValidator(properties);

        assertThat(configuredValidator.acceptedMimeTypes(MediaType.IMAGE))
                .containsExactly("image/png", "image/webp");
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

    @Test
    void validate_blankStorageKey_throwsMediaInvalidMetadata() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "   ", MediaType.IMAGE, "image/jpeg", 1024L, 800, 600, null, null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Storage key is required");
    }

    @Test
    void validate_storageKeyStartsWithSlash_throwsMediaInvalidMetadata() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "/users/123/image.jpg",
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
    void validate_storageKeyEndsWithSlash_throwsMediaInvalidMetadata() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/", MediaType.IMAGE, "image/jpeg", 1024L, 800, 600, null, null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Storage key format is invalid");
    }

    @Test
    void validate_maxMediaSizeMegabytesZero_throwsServiceUnavailable() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        1024L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 0))
                .isInstanceOf(AppException.class)
                .hasMessage("Media size system setting must be positive");
    }

    @Test
    void validate_zeroWidth_throwsMediaInvalidMetadata() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        1024L,
                        0,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Media width and height must be positive");
    }

    @Test
    void validate_negativeDurationForVideo_throwsMediaInvalidMetadata() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/video.mp4",
                        MediaType.VIDEO,
                        "video/mp4",
                        1024L,
                        800,
                        600,
                        -1,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("Duration must be non-negative");
    }

    @Test
    void validate_blurhashBlank_normalizedToNull() {
        ValidatedMediaMetadata metadata =
                validator.validate(
                        new MediaUploadCompleteRequest(
                                "users/123/media/image.jpg",
                                MediaType.IMAGE,
                                "image/jpeg",
                                1024L,
                                800,
                                600,
                                null,
                                "   "),
                        100);

        assertThat(metadata.blurhash()).isNull();
    }

    @Test
    void validate_fileSizeZero_throwsMediaInvalidMetadata() {
        MediaUploadCompleteRequest request =
                new MediaUploadCompleteRequest(
                        "users/123/media/image.jpg",
                        MediaType.IMAGE,
                        "image/jpeg",
                        0L,
                        800,
                        600,
                        null,
                        null);

        assertThatThrownBy(() -> validator.validate(request, 100))
                .isInstanceOf(AppException.class)
                .hasMessage("File size must be positive");
    }
}
