package com.app.modules.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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

    @Test
    void presignPutObject_nullTtl_throwsStorageNotConfigured() {
        MediaProperties properties = configuredProperties();
        properties.getR2().setUploadUrlTtl(null);
        R2ObjectStoragePresignService service = new R2ObjectStoragePresignService(properties);

        assertThatThrownBy(
                        () -> service.presignPutObject("users/u/media/file.jpg", "image/jpeg", 1))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
    }

    @Test
    void presignPutObject_ttlTooShort_throwsStorageNotConfigured() {
        MediaProperties properties = configuredProperties();
        properties.getR2().setUploadUrlTtl(Duration.ofSeconds(30));
        R2ObjectStoragePresignService service = new R2ObjectStoragePresignService(properties);

        assertThatThrownBy(
                        () -> service.presignPutObject("users/u/media/file.jpg", "image/jpeg", 1))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
    }

    @Test
    void presignPutObject_blankEndpoint_throwsStorageNotConfigured() {
        MediaProperties properties = configuredProperties();
        properties.getR2().setEndpoint("   ");
        R2ObjectStoragePresignService service = new R2ObjectStoragePresignService(properties);

        assertThatThrownBy(
                        () -> service.presignPutObject("users/u/media/file.jpg", "image/jpeg", 1))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
    }

    @Test
    void presignPutObject_sdkFailure_logsErrorBeforeConvertingToAppException() {
        MediaProperties properties = configuredProperties();
        properties.getR2().setEndpoint("http://bad uri with spaces.example.com");
        R2ObjectStoragePresignService service = new R2ObjectStoragePresignService(properties);

        Logger logger = (Logger) LoggerFactory.getLogger(R2ObjectStoragePresignService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(
                            () ->
                                    service.presignPutObject(
                                            "users/u/media/file.jpg", "image/jpeg", 1))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ApiErrorCode.MEDIA_UPLOAD_URL_FAILED);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.ERROR);
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
