package com.app.common.config.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    void everyCursorEndpointDeclaresA400Response() {
        JsonNode doc = document();
        Set<String> cursorOperations = new LinkedHashSet<>();

        // The cursor-endpoint set is derived the same way everyPagedSchemaDeclaresItsItemType
        // derives it: any operation whose success response resolves to a schema shaped like a
        // page (an array-typed content property alongside pageInfo), not a hardcoded path list.
        forEachSuccessResponseWithContent(
                doc,
                (operationId, code, mediaType, schema) -> {
                    if (isPagedSchema(doc, resolve(doc, schema))) {
                        cursorOperations.add(operationId);
                    }
                });

        List<String> offenders = new ArrayList<>();
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    if (cursorOperations.contains(operationId)
                            && !operation.path("responses").has("400")) {
                        offenders.add(operationId);
                    }
                });

        assertThat(offenders)
                .as("every cursor endpoint must declare a 400 response for a malformed cursor")
                .isEmpty();
    }

    @Test
    void everyCursorEndpointBoundsItsLimitParameter() {
        JsonNode doc = document();
        Set<String> cursorOperations = new LinkedHashSet<>();

        // Same derivation as everyCursorEndpointDeclaresA400Response: any operation whose success
        // response is page-shaped, not a hardcoded path list.
        forEachSuccessResponseWithContent(
                doc,
                (operationId, code, mediaType, schema) -> {
                    if (isPagedSchema(doc, resolve(doc, schema))) {
                        cursorOperations.add(operationId);
                    }
                });

        List<String> offenders = new ArrayList<>();
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    if (!cursorOperations.contains(operationId)) {
                        return;
                    }
                    JsonNode limitParam = null;
                    for (JsonNode param : operation.path("parameters")) {
                        if ("limit".equals(param.path("name").asString(""))) {
                            limitParam = param;
                            break;
                        }
                    }
                    if (limitParam == null) {
                        offenders.add(operationId + " (no limit parameter)");
                        return;
                    }
                    JsonNode schema = limitParam.path("schema");
                    boolean bounded =
                            schema.path("minimum").asInt(-1) == 1
                                    && schema.path("maximum").asInt(-1) == 100;
                    if (!bounded) {
                        offenders.add(operationId);
                    }
                });

        assertThat(offenders)
                .as("every cursor endpoint must bound its limit parameter to [1, 100]")
                .isEmpty();
    }

    /**
     * A cursor page schema sits one $ref past the ApiResponse envelope: {@code ApiResponse<T>.data}
     * points at the page schema itself, which carries an array-typed {@code content} property
     * alongside {@code pageInfo}. {@code resolve} only follows one $ref hop, so the envelope's
     * {@code data} property must be resolved a second time here.
     */
    private static boolean isPagedSchema(JsonNode doc, JsonNode resolvedEnvelope) {
        if (resolvedEnvelope == null) {
            return false;
        }
        JsonNode resolvedData = resolve(doc, resolvedEnvelope.path("properties").path("data"));
        if (resolvedData == null) {
            return false;
        }
        JsonNode content = resolvedData.path("properties").path("content");
        return "array".equals(content.path("type").asString(""))
                && resolvedData.path("properties").has("pageInfo");
    }

    @Test
    void everyBodyAcceptingEndpointDeclaresA400Response() {
        JsonNode doc = document();
        List<String> offenders = new ArrayList<>();

        // Derived from requestBody presence, not a hardcoded path list: any operation that
        // accepts a body can be sent a malformed one.
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    if (operation.path("requestBody").isMissingNode()) {
                        return;
                    }
                    if (!operation.path("responses").has("400")) {
                        offenders.add(operationId);
                    }
                });

        assertThat(offenders)
                .as("every body-accepting endpoint must declare a 400 response")
                .isEmpty();
    }

    @Test
    void anonymousOperationsDeclareAnEmptySecurityArray() {
        JsonNode doc = document();
        // The fixed set of operations the source annotates @Operation(security =
        // {@SecurityRequirement(name = "")}) to opt out of the global bearerAuth requirement.
        // Unlike the other tests in this class, this set cannot be derived from the document
        // itself: the document's `security` field is exactly what is under test, so deriving the
        // expected set from it would make the test vacuous.
        Set<String> anonymousOperations =
                Set.of(
                        "POST /api/v1/auth/register",
                        "POST /api/v1/auth/login",
                        "POST /api/v1/auth/refresh",
                        "GET /api/v1/auth/verify-email",
                        "POST /api/v1/auth/verify-email/resend",
                        "POST /api/v1/auth/forgot-password",
                        "POST /api/v1/auth/reset-password",
                        "POST /api/v1/auth/oauth2/exchange",
                        "GET /api/v1/hashtags/search",
                        "GET /api/v1/hashtags/trending",
                        "GET /api/v1/users/{userId}",
                        "GET /api/v1/users/by-username/{username}");

        List<String> offenders = new ArrayList<>();
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    if (!anonymousOperations.contains(operationId)) {
                        return;
                    }
                    JsonNode security = operation.path("security");
                    if (!security.isArray() || !security.isEmpty()) {
                        offenders.add(operationId + " -> " + security);
                    }
                });

        assertThat(offenders)
                .as("every anonymous operation must declare an empty security array")
                .isEmpty();
    }

    @Test
    void hashtagSearchDeclaresFlatQueryParametersNotAModelAttributeObject() {
        JsonNode doc = document();
        JsonNode parameters =
                doc.path("paths").path("/api/v1/hashtags/search").path("get").path("parameters");

        assertThat(parameters.isArray()).as("hashtag search must declare parameters").isTrue();
        Set<String> names = new LinkedHashSet<>();
        parameters.forEach(p -> names.add(p.path("name").asString("")));

        assertThat(names)
                .as("hashtag search must bind q/cursor/limit individually, not one object param")
                .containsExactlyInAnyOrder("q", "cursor", "limit");
    }

    @Test
    void everyAuthenticatedOperationDeclaresA401Response() {
        JsonNode doc = document();
        List<String> offenders = new ArrayList<>();

        // "Authenticated" is derived from the document's own security array being non-empty,
        // exactly the property AnonymousOperationSecurityCustomizer forces to [] for the 12
        // anonymous operations - so this set does not need a hardcoded path list.
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    JsonNode security = operation.path("security");
                    boolean requiresAuth = !security.isArray() || !security.isEmpty();
                    if (requiresAuth && !operation.path("responses").has("401")) {
                        offenders.add(operationId);
                    }
                });

        assertThat(offenders)
                .as("every authenticated operation must declare a 401 response")
                .isEmpty();
    }

    @Test
    void specificOperationsDeclareTheirObservedButUndocumentedStatus() {
        JsonNode doc = document();
        // Each pair names one status the audit observed on the wire but the document did not
        // list, confirmed against source as a real, reachable outcome (not the 500s HIGH-1/
        // HIGH-6/HIGH-7/MED-5 already account for).
        //
        // The four auth entries are the 403 UserStateValidator raises for a banned, suspended,
        // deactivated, or email-unverified account. The audit's authorisation matrix skips
        // /api/v1/auth/** by design, so this whole group went unmeasured there and was only
        // caught by re-running the harness end to end.
        record Expectation(String operationId, String status) {}
        List<Expectation> expectations =
                List.of(
                        new Expectation("POST /api/v1/auth/login", "403"),
                        new Expectation("POST /api/v1/auth/refresh", "403"),
                        new Expectation("POST /api/v1/auth/reset-password", "403"),
                        new Expectation("POST /api/v1/auth/oauth2/exchange", "403"),
                        new Expectation("GET /api/v1/posts/{postId}/comments", "403"),
                        new Expectation("GET /api/v1/comments/{commentId}/replies", "403"),
                        new Expectation("DELETE /api/v1/comments/{commentId}/like", "403"),
                        new Expectation("GET /api/v1/social/users/{userId}/followers", "403"),
                        new Expectation("GET /api/v1/social/users/{userId}/following", "403"),
                        new Expectation("POST /api/v1/social/block/{targetUserId}", "409"),
                        new Expectation("GET /api/v1/hashtags/trending", "400"));

        Map<String, JsonNode> operations = new java.util.HashMap<>();
        forEachOperation(doc, operations::put);

        List<String> offenders = new ArrayList<>();
        for (Expectation expectation : expectations) {
            JsonNode operation = operations.get(expectation.operationId());
            if (operation == null || !operation.path("responses").has(expectation.status())) {
                offenders.add(expectation.operationId() + " -> " + expectation.status());
            }
        }

        assertThat(offenders)
                .as("every confirmed observed-but-undocumented status must be declared")
                .isEmpty();
    }

    @Test
    void only422IsCommentModerationRejection() {
        JsonNode doc = document();
        // Every operation that documents 422 does so for exactly one reason in this codebase:
        // COMMENT_MODERATION_REJECTED is the only ApiErrorCode carrying HttpStatus.
        // UNPROCESSABLE_ENTITY. A 422 documented for anything else (e.g. generic bean-validation
        // failure, which the server answers with 400) is a contract defect, not a valid outcome.
        Set<String> allowed422 =
                Set.of(
                        "POST /api/v1/posts/{postId}/comments",
                        "PATCH /api/v1/comments/{commentId}");

        List<String> offenders = new ArrayList<>();
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    if (operation.path("responses").has("422")
                            && !allowed422.contains(operationId)) {
                        offenders.add(operationId);
                    }
                });

        assertThat(offenders)
                .as("422 must only be documented for comment-moderation rejection")
                .isEmpty();
    }

    @Test
    void everyBodyAcceptingEndpointDeclaresA415Response() {
        JsonNode doc = document();
        // GlobalExceptionHandler answers a wrong Content-Type with 415 on every operation that
        // reads a request body, so every such operation must declare it. Derived from the
        // document's own requestBody presence rather than a hardcoded list, so a new body
        // endpoint that forgets it turns this red.
        List<String> offenders = new ArrayList<>();
        forEachOperation(
                doc,
                (operationId, operation) -> {
                    if (operation.has("requestBody") && !operation.path("responses").has("415")) {
                        offenders.add(operationId);
                    }
                });

        assertThat(offenders)
                .as("every request-body endpoint must declare the 415 it returns")
                .isEmpty();
    }

    @Test
    void userProfileByIdDocumentsOnlyTheReachableReasonFor401() {
        JsonNode doc = document();
        // Two different 401s are easy to confuse here. The operation used to document one for
        // "target account is private", which no code path produces: assemblePublicProfile
        // returns 200 with the counter fields masked instead. It does return 401 when an
        // Authorization header is present but invalid, even though the endpoint is otherwise
        // anonymous, so the response stays documented - for that reason only.
        Map<String, JsonNode> operations = new java.util.HashMap<>();
        forEachOperation(doc, operations::put);

        JsonNode operation = operations.get("GET /api/v1/users/{userId}");
        assertThat(operation).isNotNull();
        JsonNode unauthorized = operation.path("responses").path("401");
        assertThat(unauthorized.isMissingNode())
                .as("the reachable invalid-token 401 must stay documented")
                .isFalse();
        assertThat(unauthorized.path("description").asString("").toLowerCase())
                .as("the 401 must not be attributed to the account being private")
                .doesNotContain("private");
    }

    @Test
    void observedNullFieldsAreDeclaredNullableInTheSchema() {
        JsonNode doc = document();
        // A sample of the audit's originally-cited observed-null fields, one per affected schema,
        // not an exhaustive re-check of all 79 fields marked across the branch: this is a contract
        // regression guard, not a re-derivation of the whole MED-3 audit.
        record Expectation(String schema, String field) {}
        List<Expectation> expectations =
                List.of(
                        new Expectation("PostResponse", "locationName"),
                        new Expectation("PostResponse", "latitude"),
                        new Expectation("NotificationResponse", "entityId"),
                        new Expectation("NotificationResponse", "entityType"),
                        new Expectation("ConversationResponse", "lastMessageAt"),
                        new Expectation("ConversationResponse", "groupName"),
                        new Expectation("CommentResponse", "parentId"),
                        new Expectation("CommentResponse", "rootId"),
                        new Expectation("ParticipantResponse", "leftAt"),
                        new Expectation("PageInfo", "startCursor"),
                        new Expectation("PageInfo", "endCursor"));

        List<String> offenders = new ArrayList<>();
        JsonNode schemas = doc.path("components").path("schemas");
        for (Expectation expectation : expectations) {
            JsonNode property =
                    schemas.path(expectation.schema()).path("properties").path(expectation.field());
            if (property.isMissingNode()) {
                offenders.add(expectation.schema() + "." + expectation.field() + " -> not found");
                continue;
            }
            if (!isNullable(property)) {
                offenders.add(
                        expectation.schema() + "." + expectation.field() + " -> not nullable");
            }
        }

        assertThat(offenders)
                .as("every observed-null field must be declared nullable in the document")
                .isEmpty();
    }

    /**
     * OpenAPI 3.1 (this document's dialect) represents nullability as {@code type: [X, "null"]};
     * springdoc also still recognises the OpenAPI 3.0 {@code nullable: true} keyword on some schema
     * shapes, so both are accepted here rather than assuming one specific rendering.
     */
    private static boolean isNullable(JsonNode property) {
        if (property.path("nullable").asBoolean(false)) {
            return true;
        }
        JsonNode type = property.path("type");
        if (type.isArray()) {
            for (JsonNode element : type) {
                if ("null".equals(element.asString(""))) {
                    return true;
                }
            }
        }
        return false;
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
