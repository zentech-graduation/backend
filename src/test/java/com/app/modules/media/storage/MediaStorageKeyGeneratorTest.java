package com.app.modules.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.common.exception.AppException;
import com.app.modules.media.enums.MediaType;

class MediaStorageKeyGeneratorTest {

    private final MediaStorageKeyGenerator generator = new MediaStorageKeyGenerator();

    @Test
    void generate_usesUserScopedUuidPathAndMimeExtension() {
        UUID userId = UUID.randomUUID();

        String storageKey = generator.generate(userId, MediaType.IMAGE, "image/jpeg");

        assertThat(storageKey).startsWith("users/" + userId + "/media/");
        assertThat(storageKey).endsWith(".jpg");
    }

    @Test
    void generate_gifImage_usesGifExtension() {
        String storageKey = generator.generate(UUID.randomUUID(), MediaType.IMAGE, "image/gif");

        assertThat(storageKey).endsWith(".gif");
    }

    @Test
    void generate_quicktimeVideo_usesMovExtension() {
        String storageKey =
                generator.generate(UUID.randomUUID(), MediaType.VIDEO, "video/quicktime");

        assertThat(storageKey).endsWith(".mov");
    }

    // The unsupported example used to be image/gif. GIF is an accepted image type now, so this
    // pins HEIC instead, which is deliberately refused rather than merely unmapped.
    @Test
    void generate_rejectsUnsupportedMimeType() {
        assertThatThrownBy(
                        () -> generator.generate(UUID.randomUUID(), MediaType.IMAGE, "image/heic"))
                .isInstanceOf(AppException.class);
    }
}
