package com.app.common.config.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.ClientConfiguration;

@ExtendWith(MockitoExtension.class)
class ElasticsearchConfigTest {

    @Test
    void clientConfiguration_withCredentials_setsBasicAuthHeader() {
        ElasticsearchProperties props = new ElasticsearchProperties();
        props.setUris(List.of("http://localhost:9200"));
        props.setUsername("elastic");
        props.setPassword("changeme");
        props.setConnectionTimeout(Duration.ofSeconds(5));
        props.setSocketTimeout(Duration.ofSeconds(30));

        ElasticsearchConfig config = new ElasticsearchConfig(props);
        ClientConfiguration result = config.clientConfiguration();

        String authHeader = result.getDefaultHeaders().getFirst("Authorization");
        assertThat(authHeader).isNotNull().startsWith("Basic ");
    }

    @Test
    void clientConfiguration_withBlankUsername_omitsAuthHeader() {
        ElasticsearchProperties props = new ElasticsearchProperties();
        props.setUris(List.of("http://localhost:9200"));
        props.setUsername("");
        props.setPassword("changeme");
        props.setConnectionTimeout(Duration.ofSeconds(5));
        props.setSocketTimeout(Duration.ofSeconds(30));

        ElasticsearchConfig config = new ElasticsearchConfig(props);
        ClientConfiguration result = config.clientConfiguration();

        String authHeader = result.getDefaultHeaders().getFirst("Authorization");
        assertThat(authHeader).isNull();
    }

    @Test
    void clientConfiguration_timeoutsForwardedCorrectly() {
        Duration connectTimeout = Duration.ofSeconds(3);
        Duration socketTimeout = Duration.ofSeconds(45);

        ElasticsearchProperties props = new ElasticsearchProperties();
        props.setUris(List.of("http://localhost:9200"));
        props.setConnectionTimeout(connectTimeout);
        props.setSocketTimeout(socketTimeout);

        ElasticsearchConfig config = new ElasticsearchConfig(props);
        ClientConfiguration result = config.clientConfiguration();

        assertThat(result.getConnectTimeout()).isEqualTo(connectTimeout);
        assertThat(result.getSocketTimeout()).isEqualTo(socketTimeout);
    }
}
