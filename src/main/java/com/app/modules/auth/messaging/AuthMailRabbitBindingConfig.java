package com.app.modules.auth.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds auth mail events to the mail queue without moving auth event contracts into common. */
@Configuration
public class AuthMailRabbitBindingConfig {

    @Bean
    Binding mailUserRegisteredBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(mailQueue, socialEventsExchange, AuthEventTypes.USER_REGISTERED_V1);
    }

    @Bean
    Binding mailEmailVerificationRequestedBinding(
            Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(
                mailQueue,
                socialEventsExchange,
                AuthEventTypes.AUTH_EMAIL_VERIFICATION_REQUESTED_V1);
    }

    @Bean
    Binding mailPasswordResetRequestedBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(
                mailQueue, socialEventsExchange, AuthEventTypes.AUTH_PASSWORD_RESET_REQUESTED_V1);
    }

    @Bean
    Binding mailPasswordChangedBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(
                mailQueue, socialEventsExchange, AuthEventTypes.AUTH_PASSWORD_CHANGED_V1);
    }

    @Bean
    Binding mailOauthAccountNoPasswordBinding(Queue mailQueue, TopicExchange socialEventsExchange) {
        return bindMailQueue(
                mailQueue, socialEventsExchange, AuthEventTypes.AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1);
    }

    private static Binding bindMailQueue(
            Queue mailQueue, TopicExchange socialEventsExchange, String routingKey) {
        return BindingBuilder.bind(mailQueue).to(socialEventsExchange).with(routingKey);
    }
}
