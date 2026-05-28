package com.app.modules.media.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.messaging.MediaEventTypes;
import com.app.modules.media.service.MediaEventService;

@Service
public class MediaEventServiceImpl implements MediaEventService {

    private static final String AGGREGATE_TYPE_MEDIA_ASSET = "media_asset";

    private final OutboxService outboxService;

    public MediaEventServiceImpl(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void publishMediaUploaded(MediaAsset mediaAsset) {
        outboxService.enqueue(
                MediaEventTypes.MEDIA_UPLOADED_V1,
                MediaEventTypes.MEDIA_UPLOADED_V1,
                AGGREGATE_TYPE_MEDIA_ASSET,
                mediaAsset.getId(),
                mediaAsset.getUserId(),
                Map.of(
                        "mediaAssetId",
                        mediaAsset.getId().toString(),
                        "userId",
                        mediaAsset.getUserId().toString(),
                        "mediaType",
                        mediaAsset.getMediaType().name().toLowerCase()));
    }
}
