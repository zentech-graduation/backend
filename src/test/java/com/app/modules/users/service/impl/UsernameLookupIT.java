package com.app.modules.users.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.service.MailService;
import com.app.modules.users.dto.response.PublicUserProfileResponse;
import com.app.modules.users.service.UserService;

/**
 * Proves username lookup is case-sensitive, that absence, soft deletion, and block-hiding are
 * indistinguishable to the caller, and that a UUID-shaped path segment cannot cross into the
 * id-keyed endpoint.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "app.outbox.publisher.enabled=false",
            "app.post.seed.enabled=false",
            "app.hashtag.seed.enabled=false"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class UsernameLookupIT {

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
        r.add("JWT_SECRET", () -> "username-lookup-it-secret-32-chars-minimum!!");
        r.add("JWT_ISSUER", () -> "https://username-lookup.it.local");
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

    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TestRestTemplate restTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void byUsername_exactMatch_returnsProfile() {
        UUID id = insertUser("jane_doe", false, false);

        PublicUserProfileResponse response = userService.getUserProfileByUsername(null, "jane_doe");

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.username()).isEqualTo("jane_doe");
    }

    @Test
    void byUsername_caseVariation_returnsNotFound() {
        insertUser("jane_doe", false, false);

        // Pins the case-sensitive contract. users.username carries a plain UNIQUE on the raw
        // column, so a case-insensitive lookup could resolve to two legal accounts. If this ever
        // becomes case-insensitive it must be a conscious migration, not an accident.
        assertThatThrownBy(() -> userService.getUserProfileByUsername(null, "Jane_Doe"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);
    }

    @Test
    void byUsername_caseVariantAccountsBothExist_eachResolvesToItsOwnRow() {
        UUID lower = insertUser("alice", false, false);
        UUID upper = insertUser("Alice", false, false);

        assertThat(userService.getUserProfileByUsername(null, "alice").id()).isEqualTo(lower);
        assertThat(userService.getUserProfileByUsername(null, "Alice").id()).isEqualTo(upper);
        assertThat(lower).isNotEqualTo(upper);
    }

    @Test
    void byUsername_nonExistent_returnsNotFound() {
        assertThatThrownBy(() -> userService.getUserProfileByUsername(null, "nobody_here"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);
    }

    @Test
    void byUsername_softDeletedAccount_returnsNotFound() {
        insertUser("ghostuser", false, true);

        assertThatThrownBy(() -> userService.getUserProfileByUsername(null, "ghostuser"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);
    }

    @Test
    void byUsername_blockedViewer_returnsNotFound() {
        UUID target = insertUser("target", false, false);
        UUID viewer = insertUser("viewer", false, false);
        block(target, viewer);

        assertThatThrownBy(() -> userService.getUserProfileByUsername(viewer, "target"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);
    }

    @Test
    void byUsername_absentAndSoftDeleted_areIndistinguishableOverHttp() {
        insertUser("ghostuser", false, true);

        ResponseEntity<String> absent = get("nobody_here");
        ResponseEntity<String> deleted = get("ghostuser");

        assertThat(absent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleted.getStatusCode()).isEqualTo(absent.getStatusCode());
        // Bodies must be identical apart from the per-response timestamp; any other divergence
        // would let an anonymous caller tell "no such account" from "account was deleted".
        assertThat(withoutTimestamp(deleted.getBody()))
                .isEqualTo(withoutTimestamp(absent.getBody()));
    }

    private static String withoutTimestamp(String body) {
        return body.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"<normalized>\"");
    }

    @Test
    void byUsername_hyphenatedUuid_isRejectedBeforeAnyLookup() {
        // A hyphenated UUID is 36 characters and contains '-', so it fails both the length ceiling
        // and the character class. It can never collide with a real username.
        ResponseEntity<String> response = get(UUID.randomUUID().toString());

        // Path and query constraint violations surface as HandlerMethodValidationException, which
        // GlobalExceptionHandler maps to 400. Only request-body violations return 422.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void byUsername_uuidShapedWithoutHyphens_doesNotResolveAnyUserById() {
        UUID id = insertUser("realuser", false, false);
        String hexOnly = id.toString().replace("-", "");

        // 32 hex characters passes the character class but exceeds the 30-character ceiling, so it
        // is rejected rather than being routed to the id-keyed endpoint.
        ResponseEntity<String> response = get(hexOnly);

        // Path and query constraint violations surface as HandlerMethodValidationException, which
        // GlobalExceptionHandler maps to 400. Only request-body violations return 422.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void byUsername_queryCountMatchesIdLookup() {
        UUID target = insertUser("target", false, false);
        UUID viewer = insertUser("viewer", false, false);

        Statistics stats = statistics();
        userService.getUserProfileByUsername(viewer, "target");

        stats.clear();
        userService.getUserProfile(viewer, target);
        long byId = stats.getPrepareStatementCount();

        stats.clear();
        userService.getUserProfileByUsername(viewer, "target");
        long byUsername = stats.getPrepareStatementCount();

        assertThat(byUsername).isEqualTo(byId);
    }

    private ResponseEntity<String> get(String username) {
        return restTemplate.getForEntity(
                "/api/v1/users/by-username/{username}", String.class, username);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private UUID insertUser(String username, boolean isPrivate, boolean deleted) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, email, display_name, is_private, is_verified,"
                        + " deleted_at) VALUES (?, ?, ?, ?, false, ?) RETURNING id",
                UUID.class,
                username,
                username + "@example.com",
                username,
                isPrivate,
                deleted ? java.time.OffsetDateTime.now() : null);
    }

    private void block(UUID blocker, UUID blocked) {
        jdbcTemplate.update(
                "INSERT INTO blocks(blocker_id, blocked_id) VALUES (?, ?)", blocker, blocked);
    }
}
