package com.app.modules.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.common.security.jwt.JwtTokenProvider;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "app.outbox.publisher.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class EventsControllerIT {

    private static final String TEST_JWT_SECRET = "events-it-secret-32-chars-minimum-length!";
    private static final String TEST_JWT_ISSUER = "https://events-it.test.local";
    private static final String TEST_JWT_AUDIENCE = "App";

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
        r.add("JWT_SECRET", () -> TEST_JWT_SECRET);
        r.add("JWT_ISSUER", () -> TEST_JWT_ISSUER);
        r.add("JWT_AUDIENCE", () -> TEST_JWT_AUDIENCE);
        r.add("ACCESS_TOKEN_TTL", () -> 900L);
        r.add("REFRESH_TOKEN_TTL", () -> 3600L);
        r.add("APP_BASE_URL", () -> "http://localhost:8080");
        r.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        r.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        r.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        r.add("MAIL_FROM_NAME", () -> "App Events IT");
        r.add("MAIL_APP_NAME", () -> "App");
        r.add("FRONTEND_BASE_URL", () -> "http://localhost:3000");
        r.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        r.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        r.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_events WHERE event_type LIKE 'rec.%'");
        user =
                userRepository.save(
                        activeUser("events_" + UUID.randomUUID().toString().substring(0, 8)));
    }

    @Test
    void ingestEvents_validBatch_returns202AndEnqueuesOutboxRow() {
        Map<String, Object> batch = validBatch(2);

        ResponseEntity<Map> response = postWithToken("/api/v1/events", batch, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'rec.impression.batch.v1'",
                        Integer.class);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void ingestEvents_withoutJwt_returns401() {
        Map<String, Object> batch = validBatch(1);

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/events", HttpMethod.POST, new HttpEntity<>(batch), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ingestEvents_emptyBatch_returns400() {
        Map<String, Object> batch = validBatch(0);

        ResponseEntity<Map> response = postWithToken("/api/v1/events", batch, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ingestEvents_oversizedBatch_returns400() {
        // Default app.recommendation.events.max-batch-size is 50; the cap is enforced in the
        // ingestion service so the config knob stays authoritative.
        Map<String, Object> batch = validBatch(51);

        ResponseEntity<Map> response = postWithToken("/api/v1/events", batch, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'rec.impression.batch.v1'",
                        Integer.class);
        assertThat(rowCount).isZero();
    }

    @Test
    void ingestEvents_negativePosition_returns400() {
        Map<String, Object> item =
                Map.of(
                        "clientEventId",
                        UUID.randomUUID().toString(),
                        "type",
                        "impression",
                        "postId",
                        UUID.randomUUID().toString(),
                        "position",
                        -1,
                        "source",
                        "cf");
        Map<String, Object> batch =
                Map.of(
                        "sessionId", UUID.randomUUID().toString(),
                        "requestId", UUID.randomUUID().toString(),
                        "items", List.of(item));

        ResponseEntity<Map> response = postWithToken("/api/v1/events", batch, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> postWithToken(String path, Object body, User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtFor(user));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private Map<String, Object> validBatch(int itemCount) {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add(
                    Map.of(
                            "clientEventId",
                            UUID.randomUUID().toString(),
                            "type",
                            "impression",
                            "postId",
                            UUID.randomUUID().toString(),
                            "position",
                            i,
                            "source",
                            "cf"));
        }
        return Map.of(
                "sessionId", UUID.randomUUID().toString(),
                "requestId", UUID.randomUUID().toString(),
                "items", items);
    }

    private String jwtFor(User user) {
        return jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
    }

    private static User activeUser(String username) {
        return User.builder()
                .email(username + "@test.local")
                .username(username)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .isPrivate(false)
                .isVerified(true)
                .build();
    }
}
