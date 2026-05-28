package com.app.modules.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.enums.MediaType;
import com.app.modules.media.messaging.MediaEventTypes;

@ExtendWith(MockitoExtension.class)
class MediaEventServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000456");

    @Mock private OutboxService outboxService;

    @Test
    void publishMediaUploaded_enqueuesSmallVersionedPayload() {
        MediaEventServiceImpl service = new MediaEventServiceImpl(outboxService);
        MediaAsset asset =
                MediaAsset.builder()
                        .id(MEDIA_ID)
                        .userId(USER_ID)
                        .mediaType(MediaType.IMAGE)
                        .build();
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);

        service.publishMediaUploaded(asset);

        verify(outboxService)
                .enqueue(
                        org.mockito.ArgumentMatchers.eq(MediaEventTypes.MEDIA_UPLOADED_V1),
                        org.mockito.ArgumentMatchers.eq(MediaEventTypes.MEDIA_UPLOADED_V1),
                        org.mockito.ArgumentMatchers.eq("media_asset"),
                        org.mockito.ArgumentMatchers.eq(MEDIA_ID),
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        dataCaptor.capture());
        assertThat(dataCaptor.getValue())
                .containsEntry("mediaAssetId", MEDIA_ID.toString())
                .containsEntry("userId", USER_ID.toString())
                .containsEntry("mediaType", "image");
        assertThat(dataCaptor.getValue()).doesNotContainKeys("cdnUrl", "storageKey", "mimeType");
    }
}
