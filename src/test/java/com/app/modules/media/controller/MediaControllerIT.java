package com.app.modules.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

import com.app.common.security.jwt.JwtTokenProvider;
import com.app.modules.mail.service.MailService;
import com.app.modules.media.storage.ObjectStorageMetadataService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class MediaControllerIT {

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
        r.add("JWT_SECRET", () -> "media-controller-it-secret-32-chars-min!!!!");
        r.add("JWT_ISSUER", () -> "https://media.it.local");
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
        r.add("app.outbox.publisher.enabled", () -> false);
        r.add("app.media.cdn-base-url", () -> "https://cdn.it.local");
    }

    @MockitoBean private MailService mailService;

    // Object storage is external I/O, so stubbing it here is the same exemption MailService uses.
    @MockitoBean private ObjectStorageMetadataService objectStorageMetadataService;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private record TestUser(UUID id, String token) {}

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void completeUpload_objectMissingFromStorage_returns422AndPersistsNoRow() {
        TestUser user = createUser("media_absent_owner");
        String storageKey = "users/%s/media/absent.jpg".formatted(user.id());
        when(objectStorageMetadataService.findObjectMetadata(storageKey))
                .thenReturn(Optional.empty());

        ResponseEntity<Map> response = completeUpload(user, storageKey, "image/jpeg", 1024L);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).containsEntry("code", "MEDIA_OBJECT_NOT_UPLOADED");
        assertThat(countMediaAssets(storageKey)).isZero();
    }

    @Test
    void completeUpload_storedObjectSizeDiffersFromSubmittedMetadata_returns422AndPersistsNoRow() {
        TestUser user = createUser("media_mismatch_owner");
        String storageKey = "users/%s/media/mismatch.jpg".formatted(user.id());
        when(objectStorageMetadataService.findObjectMetadata(storageKey))
                .thenReturn(
                        Optional.of(
                                new ObjectStorageMetadataService.StoredObjectMetadata(
                                        2048L, "image/jpeg")));

        ResponseEntity<Map> response = completeUpload(user, storageKey, "image/jpeg", 1024L);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).containsEntry("code", "MEDIA_OBJECT_METADATA_MISMATCH");
        assertThat(countMediaAssets(storageKey)).isZero();
    }

    @Test
    void completeUpload_storageUnreachable_returns503AndPersistsNoRow() {
        TestUser user = createUser("media_outage_owner");
        String storageKey = "users/%s/media/outage.jpg".formatted(user.id());
        when(objectStorageMetadataService.findObjectMetadata(storageKey))
                .thenThrow(
                        new com.app.common.exception.AppException(
                                com.app.common.enums.ApiErrorCode.MEDIA_STORAGE_UNAVAILABLE));

        ResponseEntity<Map> response = completeUpload(user, storageKey, "image/jpeg", 1024L);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("code", "MEDIA_STORAGE_UNAVAILABLE");
        assertThat(countMediaAssets(storageKey)).isZero();
    }

    @Test
    void completeUpload_storedObjectMatchesSubmittedMetadata_returns201AndPersistsRow() {
        TestUser user = createUser("media_match_owner");
        String storageKey = "users/%s/media/present.jpg".formatted(user.id());
        when(objectStorageMetadataService.findObjectMetadata(storageKey))
                .thenReturn(
                        Optional.of(
                                new ObjectStorageMetadataService.StoredObjectMetadata(
                                        1024L, "image/jpeg")));

        ResponseEntity<Map> response = completeUpload(user, storageKey, "image/jpeg", 1024L);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(countMediaAssets(storageKey)).isEqualTo(1);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> completeUpload(
            TestUser user, String storageKey, String mimeType, long fileSize) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body =
                """
				{"storageKey":"%s","mediaType":"IMAGE","mimeType":"%s","fileSize":%d,\
				"width":800,"height":600}"""
                        .formatted(storageKey, mimeType, fileSize);
        return rest.exchange(
                "/api/v1/media/upload-complete",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private Integer countMediaAssets(String storageKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_assets WHERE storage_key = ?",
                Integer.class,
                storageKey);
    }

    private TestUser createUser(String username) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role, status, is_private, is_verified) "
                        + "VALUES (?, ?, ?, CAST('user' AS user_role), 'active', FALSE, TRUE)",
                id,
                username,
                username + "@test.local");
        return new TestUser(id, jwtTokenProvider.generateAccessToken(id, "USER"));
    }
}
