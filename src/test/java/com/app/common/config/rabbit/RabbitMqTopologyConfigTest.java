package com.app.common.config.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RabbitMqTopologyConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(RabbitMqTopologyConfig.class);

    @Test
    void declaresOnlyMailQueuesAsActiveQueues() {
        contextRunner.run(
                context -> {
                    Set<String> queueNames =
                            context.getBeansOfType(Queue.class).values().stream()
                                    .map(Queue::getName)
                                    .collect(java.util.stream.Collectors.toSet());

                    assertThat(queueNames)
                            .containsExactlyInAnyOrder(
                                    RabbitMqTopologyConfig.MAIL_QUEUE,
                                    RabbitMqTopologyConfig.MAIL_DEAD_LETTER_QUEUE);
                    assertThat(queueNames)
                            .doesNotContain(
                                    RabbitMqTopologyConfig.NOTIFICATION_QUEUE,
                                    RabbitMqTopologyConfig.AUDIT_LOG_QUEUE,
                                    RabbitMqTopologyConfig.MODERATION_QUEUE,
                                    RabbitMqTopologyConfig.SEARCH_INDEX_QUEUE);
                });
    }

    @Test
    void declaresDomainEventAndDeadLetterExchanges() {
        contextRunner.run(
                context -> {
                    List<String> exchangeNames =
                            context.getBeansOfType(TopicExchange.class).values().stream()
                                    .map(TopicExchange::getName)
                                    .toList();

                    assertThat(exchangeNames)
                            .containsExactlyInAnyOrder(
                                    RabbitMqTopologyConfig.SOCIAL_EVENTS_EXCHANGE,
                                    RabbitMqTopologyConfig.SOCIAL_EVENTS_DEAD_LETTER_EXCHANGE);
                });
    }

    @Test
    void bindsMailQueueToMailEventRoutingKeysOnly() {
        contextRunner.run(
                context -> {
                    List<String> mailRoutingKeys =
                            context.getBeansOfType(Binding.class).values().stream()
                                    .filter(
                                            binding ->
                                                    RabbitMqTopologyConfig.MAIL_QUEUE.equals(
                                                            binding.getDestination()))
                                    .filter(
                                            binding ->
                                                    RabbitMqTopologyConfig.SOCIAL_EVENTS_EXCHANGE
                                                            .equals(binding.getExchange()))
                                    .map(Binding::getRoutingKey)
                                    .toList();

                    assertThat(mailRoutingKeys)
                            .containsExactlyInAnyOrderElementsOf(
                                    RabbitMqTopologyConfig.MAIL_EVENT_ROUTING_KEYS);
                });
    }

    @Test
    void doesNotBindFutureQueues() {
        contextRunner.run(
                context -> {
                    Set<String> futureQueues =
                            Set.of(
                                    RabbitMqTopologyConfig.NOTIFICATION_QUEUE,
                                    RabbitMqTopologyConfig.AUDIT_LOG_QUEUE,
                                    RabbitMqTopologyConfig.MODERATION_QUEUE,
                                    RabbitMqTopologyConfig.SEARCH_INDEX_QUEUE);

                    assertThat(context.getBeansOfType(Binding.class).values())
                            .noneMatch(binding -> futureQueues.contains(binding.getDestination()));
                });
    }

    @Test
    void bindsMailDeadLetterQueueToDeadLetterExchange() {
        contextRunner.run(
                context -> {
                    List<String> deadLetterRoutingKeys =
                            context.getBeansOfType(Binding.class).values().stream()
                                    .filter(
                                            binding ->
                                                    RabbitMqTopologyConfig.MAIL_DEAD_LETTER_QUEUE
                                                            .equals(binding.getDestination()))
                                    .filter(
                                            binding ->
                                                    RabbitMqTopologyConfig
                                                            .SOCIAL_EVENTS_DEAD_LETTER_EXCHANGE
                                                            .equals(binding.getExchange()))
                                    .map(Binding::getRoutingKey)
                                    .toList();

                    assertThat(deadLetterRoutingKeys)
                            .containsExactly(RabbitMqTopologyConfig.MAIL_DEAD_LETTER_ROUTING_KEY);
                });
    }
}
