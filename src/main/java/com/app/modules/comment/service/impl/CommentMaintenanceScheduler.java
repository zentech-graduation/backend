package com.app.modules.comment.service.impl;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.comment.config.CommentProperties;
import com.app.modules.comment.repository.CommentIdempotencyRepository;

/** Periodically purges expired comment write-idempotency rows. */
@Component
public class CommentMaintenanceScheduler {

    private final CommentIdempotencyRepository idempotencyRepository;
    private final CommentProperties properties;

    public CommentMaintenanceScheduler(
            CommentIdempotencyRepository idempotencyRepository, CommentProperties properties) {
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
