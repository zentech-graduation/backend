package com.app.common.inbox.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.app.common.inbox.enums.ProcessedMessageResult;
import com.app.common.inbox.repository.ProcessedMessageRepository;
import com.app.common.inbox.service.ProcessedMessageService;

@Service
public class ProcessedMessageServiceImpl implements ProcessedMessageService {

    private final ProcessedMessageRepository processedMessageRepository;

    public ProcessedMessageServiceImpl(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    @Override
    @Transactional
    public ProcessedMessageResult processOnce(
            String consumerName, UUID eventId, String eventType, Runnable handler) {
        Assert.hasText(consumerName, "consumerName must not be blank");
        Assert.notNull(eventId, "eventId must not be null");
        Assert.hasText(eventType, "eventType must not be blank");
        Assert.notNull(handler, "handler must not be null");

        boolean inserted =
                processedMessageRepository
                        .insertIfAbsent(consumerName, eventId, eventType)
                        .isPresent();
        if (!inserted) {
            return ProcessedMessageResult.DUPLICATE;
        }

        handler.run();
        return ProcessedMessageResult.PROCESSED;
    }
}
