package com.app.common.config.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.app.common.config.app.AppProperties;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Configures the global OpenAPI metadata, server URL, and Bearer JWT security scheme consumed by
 * springdoc-openapi.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Path to the real-time guide, relative to the repository root.
     *
     * <p>A relative path rather than a URL because the document is versioned with the code. A URL
     * would name a host this configuration does not know and could not keep correct across
     * environments.
     */
    private static final String REALTIME_GUIDE_PATH = "docs/modules/WEBSOCKET_GUIDE.md";

    // The description names the real-time surface because the generated document cannot describe
    // it: OpenAPI has no vocabulary for a STOMP destination, so a consumer reading this document
    // alone finds no mention of WebSockets and polls for everything, including the four domains
    // that are pushed.
    private static final String REST_AND_REALTIME_DESCRIPTION =
            "REST API for the App social network. "
                    + "This document covers the REST surface only. A real-time surface also"
                    + " exists: STOMP over SockJS at /ws/comments, /ws/posts, /ws/notifications"
                    + " and /ws/messages, authenticated with a single-use ticket from"
                    + " POST /api/v1/auth/ws-ticket. Comments, post likes, notifications and"
                    + " direct messages are pushed; everything else, including the"
                    + " escalated-report count, must be polled. OpenAPI cannot describe a STOMP"
                    + " destination, so the destinations, the handshake, the ticket flow and the"
                    + " list of what is pushed are documented in "
                    + REALTIME_GUIDE_PATH
                    + ".";

    private final AppProperties appProperties;

    public OpenApiConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Produces the global {@link OpenAPI} bean. All operations inherit the {@code bearerAuth}
     * security requirement; individual public endpoints override with {@code security = {}}.
     *
     * @return configured OpenAPI instance
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("App API")
                                .version("1.0.0")
                                .description(REST_AND_REALTIME_DESCRIPTION))
                .externalDocs(
                        new ExternalDocumentation()
                                .description(
                                        "Real-time surface: STOMP over SockJS destinations, the"
                                                + " handshake, and the ticket flow")
                                .url(REALTIME_GUIDE_PATH))
                .addServersItem(new Server().url(appProperties.baseUrl()))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
