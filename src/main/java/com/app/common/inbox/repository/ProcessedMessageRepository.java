package com.app.common.inbox.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.common.inbox.entity.ProcessedMessage;

public interface ProcessedMessageRepository
        extends Repository<ProcessedMessage, UUID>, ProcessedMessageRepositoryCustom {

    Optional<ProcessedMessage> findByConsumerNameAndEventId(String consumerName, UUID eventId);
}
