package com.app.common.messaging.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables shared RabbitMQ consumer configuration properties. */
@Configuration
@EnableConfigurationProperties(ConsumerRetryProperties.class)
public class MessagingConsumerConfig {}
