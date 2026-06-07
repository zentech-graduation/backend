package com.app.modules.post.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the post module's Elasticsearch index seed runner. Bound to the {@code
 * app.post} namespace.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.post")
public class PostProperties {

    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = true;
    }
}
