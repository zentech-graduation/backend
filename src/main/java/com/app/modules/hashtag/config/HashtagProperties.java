package com.app.modules.hashtag.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the hashtag module's trending job and Elasticsearch index seed runner. Bound to
 * the {@code app.hashtag} namespace.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.hashtag")
public class HashtagProperties {

    private Trending trending = new Trending();
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Trending {
        private Duration window = Duration.ofHours(24);
        private Duration jobDelay = Duration.ofHours(1);
        private Duration jobInitialDelay = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = true;
    }
}
