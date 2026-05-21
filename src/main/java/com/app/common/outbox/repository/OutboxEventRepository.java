package com.app.common.outbox.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.app.common.outbox.entity.OutboxEvent;

public interface OutboxEventRepository
        extends Repository<OutboxEvent, UUID>, OutboxEventRepositoryCustom {

    Optional<OutboxEvent> findById(UUID id);
}
