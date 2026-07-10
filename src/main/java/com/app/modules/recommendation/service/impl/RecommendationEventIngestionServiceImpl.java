package com.app.modules.recommendation.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.modules.recommendation.dto.ImpressionBatchEvent;
import com.app.modules.recommendation.dto.request.ClientEventBatchRequest;
import com.app.modules.recommendation.messaging.RecommendationEventTypes;
import com.app.modules.recommendation.service.RecommendationEventIngestionService;

@Service
public class RecommendationEventIngestionServiceImpl implements RecommendationEventIngestionService {

    private static final Set<String> ALLOWED_TYPES = Set.of("impression", "post_view");
    private static final Set<String> ALLOWED_SOURCES =
            Set.of("cf", "content", "graph", "trending", "explore_slot", "fallback");

    private final OutboxService outboxService;

    public RecommendationEventIngestionServiceImpl(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public void ingestBatch(UUID userId, ClientEventBatchRequest request) {
        List<ImpressionBatchEvent.Item> items =
                request.items().stream().map(item -> toBatchItem(item)).toList();
        ImpressionBatchEvent batch =
                new ImpressionBatchEvent(
                        request.sessionId(), "web", request.requestId(), items);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", batch.sessionId().toString());
        data.put("platform", batch.platform());
        data.put("requestId", batch.requestId().toString());
        data.put("items", items);
        outboxService.enqueue(
                RecommendationEventTypes.REC_IMPRESSION_BATCH_V1,
                RecommendationEventTypes.REC_IMPRESSION_BATCH_V1,
                "user",
                userId,
                userId,
                data);
    }

    private ImpressionBatchEvent.Item toBatchItem(ClientEventBatchRequest.Item item) {
        if (!ALLOWED_TYPES.contains(item.type())) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Unknown event type: " + item.type());
        }
        if (!ALLOWED_SOURCES.contains(item.source())) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Unknown source: " + item.source());
        }
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        return new ImpressionBatchEvent.Item(
                item.clientEventId(),
                item.type(),
                item.postId(),
                item.position(),
                item.source(),
                occurredAt);
    }
}
