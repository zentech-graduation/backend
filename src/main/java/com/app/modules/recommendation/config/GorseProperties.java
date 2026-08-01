package com.app.modules.recommendation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binding for the {@code app.gorse} configuration namespace. Controls the Gorse REST endpoint, API
 * key, client timeouts, and the candidate over-fetch factor used by the feed pipeline. The API key
 * must only ever come from the environment - never from database-backed settings.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.gorse")
public class GorseProperties {

    private String baseUrl = "http://localhost:8088";
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    // Over-fetch factor per source round trip; visibility filtering discards some candidates
    private int recommendMultiplier = 2;
}
