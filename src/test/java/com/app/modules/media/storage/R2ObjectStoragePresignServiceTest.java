package com.app.modules.media.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;

class R2ObjectStoragePresignServiceTest {

    @Test
    void presignPutObject_missingConfiguration_throwsStorageNotConfigured() {
        R2ObjectStoragePresignService service =
                new R2ObjectStoragePresignService(new MediaProperties());

        assertThatThrownBy(
                        () -> service.presignPutObject("users/u/media/file.jpg", "image/jpeg", 1))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
    }

    @Test
    void presignPutObject_invalidTtl_throwsStorageNotConfiguredWithoutUsingSecret() {
        MediaProperties properties = configuredProperties();
        properties.getR2().setUploadUrlTtl(Duration.ofHours(1));
        R2ObjectStoragePresignService service = new R2ObjectStoragePresignService(properties);

        assertThatThrownBy(
                        () -> service.presignPutObject("users/u/media/file.jpg", "image/jpeg", 1))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
    }

    private static MediaProperties configuredProperties() {
        MediaProperties properties = new MediaProperties();
        properties.getR2().setEndpoint("https://example.r2.cloudflarestorage.com");
        properties.getR2().setAccessKeyId("access-key");
        properties.getR2().setSecretAccessKey("secret-key");
        properties.getR2().setBucket("bucket");
        properties.getR2().setRegion("auto");
        return properties;
    }
}
