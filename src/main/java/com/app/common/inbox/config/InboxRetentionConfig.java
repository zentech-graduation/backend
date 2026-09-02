package com.app.common.inbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables scheduled retention of consumer idempotency records. */
@Configuration
@EnableConfigurationProperties(InboxRetentionProperties.class)
public class InboxRetentionConfig {}
