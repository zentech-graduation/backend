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
    void generate_rejectsUnsupportedMimeType() {
        assertThatThrownBy(
                        () -> generator.generate(UUID.randomUUID(), MediaType.IMAGE, "image/gif"))
                .isInstanceOf(AppException.class);
    }
}
