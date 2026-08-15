package com.app.modules.message.service.impl;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.message.config.MessageProperties;
import com.app.modules.message.repository.MessageIdempotencyRepository;

/** Periodically purges expired message write-idempotency rows. */
@Component
public class MessageMaintenanceScheduler {

    private final MessageIdempotencyRepository idempotencyRepository;
    private final MessageProperties properties;

    public MessageMaintenanceScheduler(
            MessageIdempotencyRepository idempotencyRepository, MessageProperties properties) {
        this.idempotencyRepository = idempotencyRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void cleanupIdempotency() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(properties.idempotencyTtlHours());
        idempotencyRepository.deleteByCreatedAtBefore(cutoff);
    }
}
