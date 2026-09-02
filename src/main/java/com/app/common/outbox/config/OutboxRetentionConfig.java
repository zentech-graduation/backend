package com.app.common.outbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables scheduled retention of published outbox rows. */
@Configuration
@EnableConfigurationProperties(OutboxRetentionProperties.class)
public class OutboxRetentionConfig {}
