package com.app.common.outbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables scheduled outbox publishing for asynchronous side-effect delivery. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxPublisherProperties.class)
public class OutboxPublisherConfig {}
