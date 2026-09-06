package com.app.modules.recommendation.service.impl;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.post.messaging.PostEventTypes;
import com.app.modules.recommendation.dto.request.ImpressionBatchRequest;
import com.app.modules.recommendation.dto.request.ImpressionRequest;
import com.app.modules.recommendation.service.ImpressionService;

@Service
public class ImpressionServiceImpl implements ImpressionService {

    private static final String AGGREGATE_TYPE_POST = "post";

    private final OutboxService outboxService;

    public ImpressionServiceImpl(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public int recordBatch(UUID viewerId, ImpressionBatchRequest request) {
        int enqueued = 0;
        for (ImpressionRequest impression : request.impressions()) {
            // Reuses post.viewed.v1 rather than adding a routing key: the recommendation consumer
            // already maps it to read feedback, and an impression is a view with a measured dwell.
            // dwellSeconds and surface are additive payload keys, so a message enqueued before they
            // existed stays readable.
            boolean inserted =
                    outboxService.enqueueOnce(
                            impression.impressionId(),
                            PostEventTypes.POST_VIEWED_V1,
                            PostEventTypes.POST_VIEWED_V1,
                            AGGREGATE_TYPE_POST,
                            impression.postId(),
                            viewerId,
                            Map.of(
                                    "postId",
                                    impression.postId().toString(),
                                    "userId",
                                    viewerId.toString(),
                                    "dwellSeconds",
                                    impression.dwellSeconds(),
                                    "surface",
                                    impression.surface().toJson()));
            if (inserted) {
                enqueued++;
            }
        }
        return enqueued;
    }
}
