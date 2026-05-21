package com.app.common.inbox.repository;

import java.util.Optional;
import java.util.UUID;

import com.app.common.inbox.entity.ProcessedMessage;

public interface ProcessedMessageRepositoryCustom {

    Optional<ProcessedMessage> insertIfAbsent(String consumerName, UUID eventId, String eventType);
}
