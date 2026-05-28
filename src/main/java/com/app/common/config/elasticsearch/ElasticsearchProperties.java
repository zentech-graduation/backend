package com.app.common.config.elasticsearch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binding for the {@code app.elasticsearch} configuration namespace. Controls the list of node
 * URIs, optional HTTP basic-auth credentials, and I/O timeouts used by the Elasticsearch client.
 * Credentials are optional — leave blank in environments where Elasticsearch security is disabled.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    private List<String> uris = new ArrayList<>(List.of("http://localhost:9200"));
    private String username;
    private String password;
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private Duration socketTimeout = Duration.ofSeconds(30);
}
