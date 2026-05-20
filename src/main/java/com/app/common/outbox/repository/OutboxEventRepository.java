package com.app.common.outbox.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.common.outbox.entity.OutboxEvent;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID>, OutboxEventRepositoryCustom {}
