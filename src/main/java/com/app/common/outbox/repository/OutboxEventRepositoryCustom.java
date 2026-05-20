package com.app.common.outbox.repository;

import com.app.common.outbox.entity.OutboxEvent;

public interface OutboxEventRepositoryCustom {

    OutboxEvent insertPending(OutboxEvent event);
}
