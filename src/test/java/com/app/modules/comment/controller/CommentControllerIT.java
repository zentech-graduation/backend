package com.app.modules.comment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.modules.mail.service.MailService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
            "app.comment.consumer.enabled=false",
            "app.comment.live.enabled=false",
            "app.outbox.publisher.enabled=false",
            "app.post.seed.enabled=false",
            "app.hashtag.seed.enabled=false"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class CommentControllerIT {

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
        r.add("JWT_SECRET", () -> "comment-controller-it-secret-32-chars-min!!!!");
        r.add("JWT_ISSUER", () -> "https://comment.it.local");
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

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record TestUser(UUID id, String token) {}

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM comment_likes");
        jdbcTemplate.update("DELETE FROM comment_write_idempotency");
        jdbcTemplate.update("DELETE FROM comments");
        jdbcTemplate.update("DELETE FROM post_media");
        jdbcTemplate.update("DELETE FROM posts");
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
    void commentLifecycle_createReplyEditLikeDelete() {
        TestUser author = registerUser("c_author");
        TestUser commenter = registerUser("c_commenter");
        UUID postId = createImagePost(author, "nice post");

        UUID commentId = createComment(commenter, postId, null, "great shot", null);
        assertThat(topLevelCount(postId)).isEqualTo(1);

        UUID replyId = createComment(author, postId, commentId, "thank you", null);
        ResponseEntity<Map> replies =
                getWithAuth("/api/v1/comments/" + commentId + "/replies", author);
        assertThat(contentOf(replies)).hasSize(1);
        assertThat(contentOf(replies).get(0).get("id")).isEqualTo(replyId.toString());

        ResponseEntity<Map> edited =
                rest.exchange(
                        "/api/v1/comments/" + commentId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(Map.of("content", "edited body"), authHeaders(commenter)),
                        Map.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) edited.getBody().get("data")).get("content"))
                .isEqualTo("edited body");

        ResponseEntity<Map> liked =
                rest.exchange(
                        "/api/v1/comments/" + commentId + "/like",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(author)),
                        Map.class);
        assertThat(liked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeCount(commentId)).isEqualTo(1);

        ResponseEntity<Map> unliked =
                rest.exchange(
                        "/api/v1/comments/" + commentId + "/like",
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(author)),
                        Map.class);
        assertThat(unliked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(likeCount(commentId)).isZero();

        // Deleting the root soft-deletes the whole subtree (root + reply).
        ResponseEntity<Map> deleted =
                rest.exchange(
                        "/api/v1/comments/" + commentId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(commenter)),
                        Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(topLevelCount(postId)).isZero();
    }

    @Test
    void createComment_idempotencyKey_replaysOriginalResponse() {
        TestUser author = registerUser("idem_author");
        TestUser commenter = registerUser("idem_commenter");
        UUID postId = createImagePost(author, "idem post");

        UUID first = createComment(commenter, postId, null, "once", "key-123");
        UUID second = createComment(commenter, postId, null, "once", "key-123");

        assertThat(second).isEqualTo(first);
        assertThat(topLevelCount(postId)).isEqualTo(1);
    }

    @Test
    void createComment_privatePostWithoutFollow_returnsForbidden() {
        TestUser owner = registerUser("priv_owner");
        TestUser stranger = registerUser("priv_stranger");
        UUID postId = createImagePost(owner, "private");
        jdbcTemplate.update("UPDATE users SET is_private = TRUE WHERE id = ?", owner.id());

        Map<String, Object> body = new HashMap<>();
        body.put("postId", postId.toString());
        body.put("content", "let me in");
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/comments",
                        HttpMethod.POST,
                        new HttpEntity<>(body, authHeaders(stranger)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("POST_COMMENTING_RESTRICTED");
    }

    @Test
    void createComment_idempotencyKeyReusedWithDifferentPayload_returnsConflict() {
        TestUser author = registerUser("conf_author");
        TestUser commenter = registerUser("conf_commenter");
        UUID postId = createImagePost(author, "conflict post");

        createComment(commenter, postId, null, "original", "conf-key");

        Map<String, Object> body = new HashMap<>();
        body.put("postId", postId.toString());
        body.put("content", "changed payload");
        HttpHeaders headers = authHeaders(commenter);
        headers.set("Idempotency-Key", "conf-key");
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/comments",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COMMENT_IDEMPOTENCY_CONFLICT");
        assertThat(topLevelCount(postId)).isEqualTo(1);
    }

    @Test
    void listComments_privatePostStranger_returnsForbidden() {
        TestUser owner = registerUser("vis_owner");
        TestUser stranger = registerUser("vis_stranger");
        UUID postId = createImagePost(owner, "private list");
        createComment(owner, postId, null, "owner comment", null);
        jdbcTemplate.update("UPDATE users SET is_private = TRUE WHERE id = ?", owner.id());

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/posts/" + postId + "/comments", stranger);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("POST_FORBIDDEN");
    }

    @Test
    void createReply_parentFromDifferentPost_returnsNotFound() {
        TestUser attacker = registerUser("xpost_attacker");
        TestUser owner = registerUser("xpost_owner");

        UUID postA = createImagePost(attacker, "attacker post");
        UUID postB = createImagePost(owner, "owner post");
        UUID parentOnB = createComment(owner, postB, null, "owner top-level", null);
        jdbcTemplate.update("UPDATE users SET is_private = TRUE WHERE id = ?", owner.id());

        Map<String, Object> body = new HashMap<>();
        body.put("postId", postA.toString());
        body.put("parentId", parentOnB.toString());
        body.put("content", "cross-post reply");
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts/" + postA + "/comments",
                        HttpMethod.POST,
                        new HttpEntity<>(body, authHeaders(attacker)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(replyCount(parentOnB)).isZero();
        assertThat(commentCount(postA)).isZero();
        ResponseEntity<Map> replies =
                getWithAuth("/api/v1/comments/" + parentOnB + "/replies", owner);
        assertThat(contentOf(replies)).isEmpty();
    }

    private UUID createComment(
            TestUser user, UUID postId, UUID parentId, String content, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("postId", postId.toString());
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        body.put("content", content);
        HttpHeaders headers = authHeaders(user);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/comments",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) ((Map<?, ?>) response.getBody().get("data")).get("id"));
    }

    private int topLevelCount(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ? AND parent_id IS NULL AND"
                        + " deleted_at IS NULL",
                Integer.class,
                postId);
    }

    private int replyCount(UUID commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT reply_count FROM comments WHERE id = ?", Integer.class, commentId);
    }

    private int commentCount(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT comment_count FROM posts WHERE id = ?", Integer.class, postId);
    }

    private int likeCount(UUID commentId) {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM comments WHERE id = ?", Integer.class, commentId);
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

    private UUID createImagePost(TestUser author, String caption) {
        UUID mediaId = insertMediaAsset(author.id());
        Map<String, Object> payload = new HashMap<>();
        payload.put("caption", caption);
        payload.put("postType", "image");
        payload.put("mediaIds", List.of(mediaId.toString()));
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) ((Map<?, ?>) response.getBody().get("data")).get("id"));
    }

    private UUID insertMediaAsset(UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO media_assets (id, user_id, storage_key, cdn_url, media_type,"
                        + " mime_type, file_size) VALUES (?, ?, ?, ?, CAST(? AS media_type), ?, ?)",
                id,
                ownerId,
                "test/" + id,
                "https://cdn.test/" + id,
                "image",
                "image/jpeg",
                1024L);
        return id;
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
    private static List<Map<?, ?>> contentOf(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return (List<Map<?, ?>>) data.get("content");
    }

    private static String uniqueIp() {
        java.util.Random rand = new java.util.Random();
        return "10." + rand.nextInt(256) + "." + rand.nextInt(256) + "." + (1 + rand.nextInt(254));
    }
}
