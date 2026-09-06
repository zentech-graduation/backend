package com.app.modules.comment.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.app.modules.comment.live.CommentWebSocketSessionRegistry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Micrometer instrumentation for the comment subsystem. */
@Component
public class CommentMetrics {

    private final MeterRegistry registry;
    private final Timer createLatency;
    private final Timer fanoutLatency;
    private final Counter idempotencyReplays;
    private final Counter wsPushFailures;

    public CommentMetrics(
            MeterRegistry registry,
            ObjectProvider<CommentWebSocketSessionRegistry> sessionRegistryProvider) {
        this.registry = registry;
        this.createLatency = registry.timer("comment.create.latency");
        this.fanoutLatency = registry.timer("comment.fanout.latency");
        this.idempotencyReplays = registry.counter("comment.idempotency.replays");
        this.wsPushFailures = registry.counter("comment.ws.push.failures");
        CommentWebSocketSessionRegistry sessionRegistry = sessionRegistryProvider.getIfAvailable();
        if (sessionRegistry != null) {
            Gauge.builder("comment.ws.connections", sessionRegistry, r -> r.totalSessions())
                    .register(registry);
        }
    }

    public Timer createLatency() {
        return createLatency;
    }

    public Timer fanoutLatency() {
        return fanoutLatency;
    }

    public void idempotencyReplay() {
        idempotencyReplays.increment();
    }

    public void moderationRejected(String reason) {
        registry.counter("comment.moderation.rejected", "reason", reason).increment();
    }

    public void wsPushFailure() {
        wsPushFailures.increment();
    }
}
