package com.app.modules.comment.observability;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Reports the comment subsystem as UP only when both Redis and RabbitMQ are reachable. */
@Component("commentSubsystem")
public class CommentSubsystemHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    public CommentSubsystemHealthIndicator(
            StringRedisTemplate redisTemplate,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    @Override
    public Health health() {
        try {
            redisTemplate.execute(connection -> connection.ping(), true);
        } catch (RuntimeException e) {
            return Health.down().withDetail("redis", e.getMessage()).build();
        }
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.execute(channel -> channel.isOpen());
            } catch (RuntimeException e) {
                return Health.down().withDetail("rabbitmq", e.getMessage()).build();
            }
        }
        return Health.up().build();
    }
}
