package com.app.modules.recommendation.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds recommendation event types to the recommendation queues on the {@code social.events} topic
 * exchange.
 *
 * <p>The persist queue receives both interaction-recorded and impression-batch events. The trending
 * queue receives only interaction-recorded events, since impressions do not feed the trending score.
 */
@Configuration
public class RecommendationRabbitBindingConfig {

    @Bean
    Binding recEventsInteractionBinding(
            Queue recEventsQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(recEventsQueue)
                .to(socialEventsExchange)
                .with(RecommendationEventTypes.REC_INTERACTION_RECORDED_V1);
    }

    @Bean
    Binding recEventsImpressionBinding(
            Queue recEventsQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(recEventsQueue)
                .to(socialEventsExchange)
                .with(RecommendationEventTypes.REC_IMPRESSION_BATCH_V1);
    }

    @Bean
    Binding recTrendingInteractionBinding(
            Queue recTrendingQueue, TopicExchange socialEventsExchange) {
        return BindingBuilder.bind(recTrendingQueue)
                .to(socialEventsExchange)
                .with(RecommendationEventTypes.REC_INTERACTION_RECORDED_V1);
    }
}
