package com.app.modules.story.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
class StoryControllerIT {

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
        r.add("JWT_SECRET", () -> "story-controller-it-secret-32-chars-min!!!!");
        r.add("JWT_ISSUER", () -> "https://story.it.local");
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

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record TestUser(UUID id, String token) {}

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM story_views");
        jdbcTemplate.update("DELETE FROM stories");
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM oauth_accounts");
        jdbcTemplate.update("DELETE FROM user_credentials");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void storyLifecycle_createFeedGetListDelete() {
        TestUser author = registerUser("story_author");
        TestUser follower = registerUser("story_follower");
        insertFollow(follower.id(), author.id(), "accepted");

        UUID storyId = createStory(author, "hello");

        // Owner reads: view count populated, seen flag absent.
        ResponseEntity<Map> ownerGet = getWithAuth("/api/v1/stories/" + storyId, author);
        assertThat(ownerGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> ownerData = (Map<?, ?>) ownerGet.getBody().get("data");
        assertThat(ownerData.get("viewCount")).isEqualTo(0);
        assertThat(ownerData.get("seen")).isNull();

        // Follower feed shows the author's tray with hasUnseen true.
        ResponseEntity<Map> feed = getWithAuth("/api/v1/stories/feed", follower);
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> trayItems = feedItems(feed);
        Map<?, ?> authorTray =
                trayItems.stream()
                        .filter(i -> i.get("userId").equals(author.id().toString()))
                        .findFirst()
                        .orElseThrow();
        assertThat(authorTray.get("hasUnseen")).isEqualTo(true);

        // Follower lists the author's stories directly.
        ResponseEntity<Map> listResponse =
                getWithAuth("/api/v1/stories/user/" + author.id(), follower);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listItems(listResponse)).hasSize(1);

        // Owner deletes; subsequent reads 404.
        ResponseEntity<Map> deleted =
                rest.exchange(
                        "/api/v1/stories/" + storyId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(author)),
                        Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> afterDelete = getWithAuth("/api/v1/stories/" + storyId, author);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(afterDelete.getBody().get("code")).isEqualTo("STORY_NOT_FOUND");
    }

    @Test
    void createStory_mediaNotOwned_returnsForbidden() {
        TestUser author = registerUser("wrong_owner");
        TestUser other = registerUser("asset_owner");
        UUID mediaId = insertMediaAsset(other.id(), "image");

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/stories",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("mediaId", mediaId.toString()), authHeaders(author)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("STORY_FORBIDDEN");
    }

    @Test
    void getStoryById_expired_returnsNotFound() {
        TestUser author = registerUser("expiry_author");
        UUID storyId = createStory(author, "will expire");
        jdbcTemplate.update(
                "UPDATE stories SET expires_at = NOW() - INTERVAL '1 hour' WHERE id = ?", storyId);

        ResponseEntity<Map> response = getWithAuth("/api/v1/stories/" + storyId, author);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("STORY_NOT_FOUND");
    }

    @Test
    void listUserStories_privateAccountWithoutFollow_returnsForbiddenThenOkAfterFollow() {
        TestUser owner = registerUser("priv_story_owner");
        TestUser stranger = registerUser("priv_story_stranger");
        createStory(owner, "private story");
        jdbcTemplate.update("UPDATE users SET is_private = TRUE WHERE id = ?", owner.id());

        ResponseEntity<Map> forbidden = getWithAuth("/api/v1/stories/user/" + owner.id(), stranger);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("code")).isEqualTo("STORY_FORBIDDEN");

        insertFollow(stranger.id(), owner.id(), "accepted");
        ResponseEntity<Map> allowed = getWithAuth("/api/v1/stories/user/" + owner.id(), stranger);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listItems(allowed)).hasSize(1);
    }

    @Test
    void listUserStories_blocked_returnsBlocked() {
        TestUser owner = registerUser("block_story_owner");
        TestUser viewer = registerUser("block_story_viewer");
        createStory(owner, "blocked story");
        jdbcTemplate.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)",
                owner.id(),
                viewer.id());

        ResponseEntity<Map> response = getWithAuth("/api/v1/stories/user/" + owner.id(), viewer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("SOCIAL_BLOCKED");
    }

    @Test
    void deleteStory_nonOwner_returnsForbidden() {
        TestUser owner = registerUser("del_story_owner");
        TestUser stranger = registerUser("del_story_stranger");
        UUID storyId = createStory(owner, "not yours");

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/stories/" + storyId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(stranger)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("STORY_FORBIDDEN");
    }

    private UUID createStory(TestUser author, String caption) {
        UUID mediaId = insertMediaAsset(author.id(), "image");
        Map<String, Object> body = new HashMap<>();
        body.put("mediaId", mediaId.toString());
        body.put("caption", caption);
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/stories",
                        HttpMethod.POST,
                        new HttpEntity<>(body, authHeaders(author)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) ((Map<?, ?>) response.getBody().get("data")).get("id"));
    }

    private UUID insertMediaAsset(UUID ownerId, String mediaType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO media_assets (id, user_id, storage_key, cdn_url, media_type,"
                        + " mime_type, file_size) VALUES (?, ?, ?, ?, CAST(? AS media_type), ?, ?)",
                id,
                ownerId,
                "test/" + id,
                "https://cdn.test/" + id,
                mediaType,
                "image".equals(mediaType) ? "image/jpeg" : "video/mp4",
                1024L);
        return id;
    }

    private void insertFollow(UUID followerId, UUID followingId, String status) {
        jdbcTemplate.update(
                "INSERT INTO follows (follower_id, following_id, status)"
                        + " VALUES (?, ?, CAST(? AS follow_status))",
                followerId,
                followingId,
                status);
    }

    private TestUser registerUser(String username) {
        String email = username + "@test.local";
        String password = "S3cur3P@ssword!";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", uniqueIp());

        Map<String, Object> registerPayload =
                Map.of(
                        "username", username,
                        "email", email,
                        "password", password,
                        "displayName", username);
        ResponseEntity<Map> reg =
                rest.exchange(
                        "/api/v1/auth/register",
                        HttpMethod.POST,
                        new HttpEntity<>(registerPayload, headers),
                        Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID id =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE username = ?", UUID.class, username);
        jdbcTemplate.update(
                "UPDATE user_credentials SET email_verified = TRUE WHERE user_id = ?", id);

        ResponseEntity<Map> login =
                rest.exchange(
                        "/api/v1/auth/login",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", email, "password", password), headers),
                        Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) login.getBody().get("data");
        return new TestUser(id, (String) data.get("accessToken"));
    }

    private HttpHeaders authHeaders(TestUser user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map> getWithAuth(String url, TestUser user) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders(user)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> feedItems(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<?, ?>>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> listItems(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<?, ?>>) response.getBody().get("data");
    }

    private static String uniqueIp() {
        Random rand = new Random();
        return "10." + rand.nextInt(256) + "." + rand.nextInt(256) + "." + (1 + rand.nextInt(254));
    }
}
