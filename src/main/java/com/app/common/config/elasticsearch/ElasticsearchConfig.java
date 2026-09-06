package com.app.common.config.elasticsearch;

import java.net.URI;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * Elasticsearch client configuration. Wires the Spring Data Elasticsearch {@link
 * org.springframework.data.elasticsearch.core.ElasticsearchOperations} infrastructure using
 * connection settings from {@link ElasticsearchProperties}. Connection timeouts, node URIs, and
 * optional credentials are all driven by environment variables.
 *
 * <p>Spring Boot auto-configures {@code elasticsearchHealthIndicator} via {@code
 * ElasticsearchHealthContributorAutoConfiguration} when this client is on the classpath — no custom
 * {@code HealthIndicator} is declared here.
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final ElasticsearchProperties properties;

    public ElasticsearchConfig(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        String[] hostAndPorts =
                properties.getUris().stream()
                        .map(
                                uri -> {
                                    URI parsed = URI.create(uri);
                                    return parsed.getHost() + ":" + parsed.getPort();
                                })
                        .toArray(String[]::new);

        ClientConfiguration.MaybeSecureClientConfigurationBuilder connected =
                ClientConfiguration.builder().connectedTo(hostAndPorts);

        String username = properties.getUsername();
        String password = properties.getPassword();

        // Guard: only apply basic auth when credentials are configured — some Elasticsearch
        // environments disable security entirely, in which case an auth header causes a 401.
        ClientConfiguration.TerminalClientConfigurationBuilder terminal =
                (username != null && !username.isBlank() && password != null && !password.isBlank())
                        ? connected.withBasicAuth(username, password)
                        : connected;

        return terminal.withConnectTimeout(properties.getConnectionTimeout())
                .withSocketTimeout(properties.getSocketTimeout())
                .build();
    }
}
