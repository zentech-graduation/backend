package com.app.common.config.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.app.common.config.app.AppProperties;

import io.swagger.v3.oas.models.Components;
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
                                .description("REST API for the App social network"))
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
