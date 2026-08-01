package com.app.common.config.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.modules.mail.service.MailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Structural contract test over the served OpenAPI document.
 *
 * <p>Asserts invariants, never counts, and derives every subject set from the document itself
 * rather than from a hardcoded list of paths. A new endpoint therefore does not require editing
 * this test: a correct one keeps it green, an under-declared one turns it red. That property is the
 * point, because the defect class this guards against was introduced one hand-written annotation at
 * a time.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
            "app.outbox.publisher.enabled=false",
            "app.post.seed.enabled=false",
            "app.hashtag.seed.enabled=false"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class OpenApiContractIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
        r.add("JWT_SECRET", () -> "openapi-contract-it-secret-32-chars-min!!");
        r.add("JWT_ISSUER", () -> "https://openapi-contract.it.local");
        r.add("JWT_AUDIENCE", () -> "App");
        r.add("ACCESS_TOKEN_TTL", () -> 900L);
        r.add("REFRESH_TOKEN_TTL", () -> 3600L);
        r.add("APP_BASE_URL", () -> "http://localhost:8080");
        r.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        r.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        r.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        r.add("MAIL_FROM_NAME", () -> "App IT");
        r.add("MAIL_APP_NAME", () -> "App");
        r.add("FRONTEND_BASE_URL", () -> "http://localhost:3000");
        r.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        r.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        r.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @MockitoBean private MailService mailService;

    @Autowired private TestRestTemplate restTemplate;

    private static JsonNode document;

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @BeforeAll
    static void resetDocument() {
        document = null;
    }

    private JsonNode document() {
        if (document == null) {
            String body = restTemplate.getForObject("/api-docs", String.class);
            assertThat(body).as("served OpenAPI document").isNotBlank();
            document = new ObjectMapper().readTree(body);
        }
        return document;
    }

    @Test
    void everySuccessResponseDeclaresTheApiResponseEnvelope() {
        JsonNode doc = document();
        List<String> offenders = new ArrayList<>();

        forEachSuccessResponseWithContent(
                doc,
                (operationId, code, mediaType, schema) -> {
                    String ref = refName(schema);
                    if ("ApiResponse".equals(ref)) {
                        // The bare envelope leaves `data` untyped, so a generated client gets
                        // Object for the payload.
                        offenders.add(operationId + " " + code + " -> bare ApiResponse");
                        return;
                    }
                    JsonNode resolved = resolve(doc, schema);
                    if (resolved == null || !resolved.path("properties").has("data")) {
                        // A payload type declared as the whole body is factually wrong: the
                        // server always wraps it in ApiResponse<T>.
                        offenders.add(
                                operationId + " " + code + " -> " + ref + " (no data property)");
                    }
                });

        assertThat(offenders)
                .as(
                        "every success response carrying a body must resolve to ApiResponse<T>"
                                + " with T declared")
                .isEmpty();
    }

    @Test
    void everyPagedSchemaDeclaresItsItemType() {
        JsonNode doc = document();
        List<String> offenders = new ArrayList<>();

        // Checked over components/schemas rather than by walking down from each operation, so a
        // page schema that is untyped is caught even while the envelope above it is also
        // under-declared. Walking from the operation would silently skip it in that state.
        doc.path("components")
                .path("schemas")
                .properties()
                .forEach(
                        entry -> {
                            JsonNode content = entry.getValue().path("properties").path("content");
                            if (!"array".equals(content.path("type").asString(""))) {
                                return;
                            }
                            JsonNode items = content.path("items");
                            if (items.isMissingNode() || items.isEmpty() || !items.has("$ref")) {
                                offenders.add(entry.getKey() + ".content.items unresolved");
                            }
                        });

        assertThat(offenders).as("every paged schema must declare a resolved item type").isEmpty();
    }

    @Test
    void everyReferencedSchemaExists() {
        JsonNode doc = document();
        Set<String> declared = new LinkedHashSet<>();
        doc.path("components").path("schemas").propertyNames().forEach(declared::add);

        Set<String> dangling = new LinkedHashSet<>();
        collectRefs(doc.path("paths"), declared, dangling);
        collectRefs(doc.path("components").path("schemas"), declared, dangling);

        assertThat(dangling)
                .as("every $ref must resolve to a member of components/schemas")
                .isEmpty();
    }

    @Test
    void noContentResponsesDeclareNoBody() {
        JsonNode doc = document();
        List<String> offenders = new ArrayList<>();

        forEachOperation(
                doc,
                (operationId, operation) ->
                        operation
                                .path("responses")
                                .properties()
                                .forEach(
                                        entry -> {
                                            if (!"204".equals(entry.getKey())) {
                                                return;
                                            }
                                            if (entry.getValue().has("content")) {
                                                // A 204 has no message body. Declaring a schema
                                                // for one invites a generator to emit a response
                                                // type that can never arrive.
                                                offenders.add(operationId + " 204");
                                            }
                                        }));

        assertThat(offenders).as("a 204 response must not declare a content block").isEmpty();
    }

    private interface ResponseVisitor {
        void visit(String operationId, String code, String mediaType, JsonNode schema);
    }

    private interface OperationVisitor {
        void visit(String operationId, JsonNode operation);
    }

    private void forEachOperation(JsonNode doc, OperationVisitor visitor) {
        doc.path("paths")
                .properties()
                .forEach(
                        pathEntry ->
                                pathEntry
                                        .getValue()
                                        .properties()
                                        .forEach(
                                                methodEntry -> {
                                                    if (!HTTP_METHODS.contains(
                                                            methodEntry.getKey())) {
                                                        return;
                                                    }
                                                    visitor.visit(
                                                            methodEntry.getKey().toUpperCase()
                                                                    + " "
                                                                    + pathEntry.getKey(),
                                                            methodEntry.getValue());
                                                }));
    }

    private void forEachSuccessResponseWithContent(JsonNode doc, ResponseVisitor visitor) {
        forEachOperation(
                doc,
                (operationId, operation) ->
                        operation
                                .path("responses")
                                .properties()
                                .forEach(
                                        responseEntry -> {
                                            String code = responseEntry.getKey();
                                            if (!code.startsWith("2")) {
                                                return;
                                            }
                                            JsonNode content =
                                                    responseEntry.getValue().path("content");
                                            content.properties()
                                                    .forEach(
                                                            mediaEntry ->
                                                                    visitor.visit(
                                                                            operationId,
                                                                            code,
                                                                            mediaEntry.getKey(),
                                                                            mediaEntry
                                                                                    .getValue()
                                                                                    .path(
                                                                                            "schema")));
                                        }));
    }

    private static String refName(JsonNode schema) {
        String ref = schema.path("$ref").asString("");
        int slash = ref.lastIndexOf('/');
        return slash < 0 ? "" : ref.substring(slash + 1);
    }

    private static JsonNode resolve(JsonNode doc, JsonNode schema) {
        if (schema == null || schema.isMissingNode()) {
            return null;
        }
        if (!schema.has("$ref")) {
            return schema.isEmpty() ? null : schema;
        }
        JsonNode target = doc.path("components").path("schemas").path(refName(schema));
        return target.isMissingNode() ? null : target;
    }

    private static void collectRefs(JsonNode node, Set<String> declared, Set<String> dangling) {
        if (node.isObject()) {
            node.properties()
                    .forEach(
                            entry -> {
                                if ("$ref".equals(entry.getKey()) && entry.getValue().isString()) {
                                    String name = refName(node);
                                    if (!name.isEmpty() && !declared.contains(name)) {
                                        dangling.add(name);
                                    }
                                } else {
                                    collectRefs(entry.getValue(), declared, dangling);
                                }
                            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, declared, dangling));
        }
    }
}
