package com.app.modules.post.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
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
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.common.config.rabbit.RabbitMqTopologyConfig;
import com.app.common.outbox.service.OutboxPublisherService;
import com.app.modules.mail.service.MailService;
import com.app.modules.post.consumer.PostIndexSyncConsumer;
import com.app.modules.post.search.PostDocument;
import com.rabbitmq.client.Channel;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "app.post.consumer.enabled=true",
            "app.messaging.consumer.max-attempts=1",
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "spring.rabbitmq.publisher-confirm-type=correlated",
            "spring.rabbitmq.publisher-returns=true",
            "spring.rabbitmq.template.mandatory=true"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostControllerIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbit =
            new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-alpine"))
                    .withExposedPorts(5672);

    @Container
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(
                            DockerImageName.parse(
                                    "docker.elastic.co/elasticsearch/elasticsearch:9.0.3"))
                    .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // Override any developer .env REDIS_PASSWORD — the test container runs without auth.
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
        r.add("app.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
        r.add("JWT_SECRET", () -> "post-controller-it-secret-32-chars-min!!!!!!");
        r.add("JWT_ISSUER", () -> "https://post.it.local");
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
        // Keep the publisher bean present so the test can drive publishDueEvents() manually, and
        // push the scheduled poll past the suite runtime so only the manual publish drains the
        // outbox — eliminating a race between the scheduler and the in-test publish.
        r.add("app.outbox.publisher.enabled", () -> true);
        r.add("app.outbox.publisher.initial-delay", () -> "PT1H");
        r.add("app.outbox.publisher.fixed-delay", () -> "PT1H");
        // Disable the startup seed runners so each test owns its index documents.
        r.add("app.hashtag.seed.enabled", () -> false);
        r.add("app.post.seed.enabled", () -> false);
    }

    @MockitoBean private MailService mailService;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ElasticsearchOperations elasticsearchOperations;
    @Autowired private OutboxPublisherService outboxPublisherService;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private PostIndexSyncConsumer postIndexSyncConsumer;

    private record TestUser(UUID id, String token) {}

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM post_edit_history");
        jdbcTemplate.update("DELETE FROM post_hashtags");
        jdbcTemplate.update("DELETE FROM post_likes");
        jdbcTemplate.update("DELETE FROM post_saves");
        jdbcTemplate.update("DELETE FROM post_media");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM hashtag_trending");
        jdbcTemplate.update("DELETE FROM hashtags");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM push_tokens");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM oauth_accounts");
        jdbcTemplate.update("DELETE FROM user_credentials");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM processed_messages");
        jdbcTemplate.update("DELETE FROM users");
        // ES cleanup is guarded: the fallback test stops the container irreversibly, so touching
        // Elasticsearch here would hang/fail once it is down. Only clear while it is still
        // running.
        if (elasticsearch.isRunning()) {
            try {
                IndexOperations ops = elasticsearchOperations.indexOps(PostDocument.class);
                if (ops.exists()) {
                    ops.delete();
                }
            } catch (RuntimeException ignored) {
                // Best-effort index teardown; never fail a test on cleanup.
            }
        }
    }

    @Test
    void createPost_mediaIdsAboveMax_returnsBadRequest() {
        TestUser author = registerUser("media_bound_author");

        List<String> oversized = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            oversized.add(UUID.randomUUID().toString());
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("postType", "carousel");
        payload.put("mediaIds", oversized);

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(1)
    void createPost_carouselWithOneMedia_returnsBadRequest() {
        TestUser author = registerUser("carousel_author");
        UUID mediaId = insertMediaAsset(author.id(), "image");

        Map<String, Object> payload = new HashMap<>();
        payload.put("postType", "carousel");
        payload.put("mediaIds", List.of(mediaId.toString()));

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(2)
    void createPost_published_persistsAndExtractsHashtags() {
        TestUser author = registerUser("hashtag_author");
        UUID mediaId = insertMediaAsset(author.id(), "image");

        UUID postId = createImagePost(author, "hello world #tag", mediaId);

        assertThat(countOf("posts")).isEqualTo(1);
        assertThat(countOf("post_media")).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM hashtags WHERE name = 'tag'", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM post_hashtags WHERE post_id = ?",
                                Integer.class,
                                postId))
                .isEqualTo(1);
        assertThat(postCountOf(author.id())).isEqualTo(1);
    }

    @Test
    @Order(3)
    void getPostById_strangerOnPublicAccount_returnsOk() {
        TestUser author = registerUser("public_author");
        TestUser stranger = registerUser("public_stranger");
        UUID postId =
                createImagePost(author, "open to all", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> response = getWithAuth("/api/v1/posts/" + postId, stranger);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(postId.toString());
    }

    @Test
    @Order(4)
    void updateCaption_owner_writesEditHistory() {
        TestUser author = registerUser("edit_author");
        TestUser stranger = registerUser("edit_stranger");
        UUID postId =
                createImagePost(author, "first caption", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> updated =
                rest.exchange(
                        "/api/v1/posts/" + postId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(Map.of("caption", "second caption"), authHeaders(author)),
                        Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> forbidden =
                rest.exchange(
                        "/api/v1/posts/" + postId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(Map.of("caption", "hijack"), authHeaders(stranger)),
                        Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> history = getWithAuth("/api/v1/posts/" + postId + "/history", author);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> content = contentOf(history);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("previousCaption")).isEqualTo("first caption");
        Map<?, ?> editor = (Map<?, ?>) content.get(0).get("editor");
        assertThat(editor.get("id")).isEqualTo(author.id().toString());
    }

    @Test
    @Order(5)
    void transitionStatus_lifecycle_validAndInvalid() {
        TestUser author = registerUser("lifecycle_author");
        UUID mediaId = insertMediaAsset(author.id(), "image");

        Map<String, Object> payload = new HashMap<>();
        payload.put("caption", "cycling #cycle");
        payload.put("postType", "image");
        payload.put("mediaIds", List.of(mediaId.toString()));
        payload.put("status", "draft");
        ResponseEntity<Map> created =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID postId =
                UUID.fromString((String) ((Map<?, ?>) created.getBody().get("data")).get("id"));
        // Drafts must not have hashtag associations yet.
        assertThat(postHashtagCount(postId)).isZero();

        assertThat(transition(author, postId, "published").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(postHashtagCount(postId)).isEqualTo(1);

        assertThat(transition(author, postId, "archived").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postHashtagCount(postId)).isZero();

        assertThat(transition(author, postId, "published").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(transition(author, postId, "draft").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    void deletePost_owner_softDeletes() {
        TestUser author = registerUser("delete_author");
        UUID postId =
                createImagePost(author, "to be removed", insertMediaAsset(author.id(), "image"));
        assertThat(postCountOf(author.id())).isEqualTo(1);

        ResponseEntity<Void> deleted =
                rest.exchange(
                        "/api/v1/posts/" + postId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(author)),
                        Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> gone = getWithAuth("/api/v1/posts/" + postId, author);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT status::text AS status, deleted_at FROM posts WHERE id = ?",
                        postId);
        assertThat(row.get("status")).isEqualTo("removed");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(postCountOf(author.id())).isZero();
    }

    @Test
    @Order(7)
    void listUserPosts_visibilityMatrix() {
        TestUser owner = registerUser("private_owner");
        TestUser viewer = registerUser("matrix_viewer");
        createImagePost(owner, "private content", insertMediaAsset(owner.id(), "image"));
        jdbcTemplate.update("UPDATE users SET is_private = TRUE WHERE id = ?", owner.id());

        String url = "/api/v1/posts/user/" + owner.id();

        ResponseEntity<Map> stranger = getWithAuth(url, viewer);
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stranger.getBody().get("code")).isEqualTo("POST_FORBIDDEN");

        jdbcTemplate.update(
                "INSERT INTO follows (follower_id, following_id, status) VALUES (?, ?, 'accepted')",
                viewer.id(),
                owner.id());
        ResponseEntity<Map> follower = getWithAuth(url, viewer);
        assertThat(follower.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(follower)).hasSize(1);

        jdbcTemplate.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)",
                owner.id(),
                viewer.id());
        // Stealth block model: a blocked target must be indistinguishable from a nonexistent one.
        ResponseEntity<Map> blocked = getWithAuth(url, viewer);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(blocked.getBody().get("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    @Order(8)
    void likePost_idempotencyAndCounter() {
        TestUser author = registerUser("like_author");
        TestUser liker = registerUser("like_liker");
        UUID postId = createImagePost(author, "like me", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> liked =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/like",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(liker)),
                        Map.class);
        assertThat(liked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) liked.getBody().get("data")).get("likeCount")).isEqualTo(1);

        ResponseEntity<Map> duplicate =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/like",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(liker)),
                        Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("POST_ALREADY_LIKED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT like_count FROM posts WHERE id = ?", Integer.class, postId))
                .isEqualTo(1);

        ResponseEntity<Map> unliked =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/like",
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(liker)),
                        Map.class);
        assertThat(unliked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) unliked.getBody().get("data")).get("likeCount")).isEqualTo(0);
    }

    @Test
    @Order(9)
    void unlikePost_concurrentDuplicate_neverReturns500() throws Exception {
        // Reproduces the audit's HIGH-6 shape directly: two concurrent unlikes of the same like
        // must resolve to exactly one 200 and one 404, never a 500 from
        // ObjectOptimisticLockingFailureException racing a load-then-delete(entity).
        TestUser author = registerUser("race_author");
        TestUser liker = registerUser("race_liker");
        UUID postId =
                createImagePost(author, "race target", insertMediaAsset(author.id(), "image"));
        rest.exchange(
                "/api/v1/posts/" + postId + "/like",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(liker)),
                Map.class);

        int attempts = 10;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(attempts);
        java.util.concurrent.CyclicBarrier barrier =
                new java.util.concurrent.CyclicBarrier(attempts);
        try {
            List<java.util.concurrent.Future<HttpStatus>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    barrier.await();
                                    return (HttpStatus)
                                            rest.exchange(
                                                            "/api/v1/posts/" + postId + "/like",
                                                            HttpMethod.DELETE,
                                                            new HttpEntity<>(authHeaders(liker)),
                                                            Map.class)
                                                    .getStatusCode();
                                }));
            }
            List<HttpStatus> statuses = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<HttpStatus> future : futures) {
                statuses.add(future.get());
            }

            assertThat(statuses)
                    .as("no concurrent unlike may surface as 500")
                    .doesNotContain(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(statuses.stream().filter(s -> s == HttpStatus.OK).count())
                    .as("exactly one concurrent unlike wins")
                    .isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == HttpStatus.NOT_FOUND).count())
                    .as("the rest cleanly lose with 404")
                    .isEqualTo(attempts - 1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @Order(9)
    void savePost_listSaved_visibilityFiltering() {
        TestUser author = registerUser("save_author");
        TestUser saver = registerUser("save_saver");
        UUID postId =
                createImagePost(author, "bookmark me", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> saved =
                rest.exchange(
                        "/api/v1/posts/" + postId + "/save",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(saver)),
                        Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> listed = getWithAuth("/api/v1/posts/saved", saver);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(listed)).hasSize(1);

        // The author blocking the saver must remove the post from the saved list.
        jdbcTemplate.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)",
                author.id(),
                saver.id());
        ResponseEntity<Map> filtered = getWithAuth("/api/v1/posts/saved", saver);
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(filtered)).isEmpty();
    }

    @Test
    @Order(10)
    void searchPosts_indexed_returnsMatches() throws java.io.IOException {
        TestUser author = registerUser("search_author");
        TestUser hidden = registerUser("search_hidden");
        TestUser viewer = registerUser("search_viewer");
        UUID visiblePostId =
                createImagePost(
                        author, "sunset over hills", insertMediaAsset(author.id(), "image"));
        // The hidden author's published post is also indexed but must be filtered out at search
        // time because the account is private and the viewer does not follow it.
        createImagePost(hidden, "sunset secret", insertMediaAsset(hidden.id(), "image"));
        jdbcTemplate.update("UPDATE users SET is_private = TRUE WHERE id = ?", hidden.id());

        // Both published posts enqueue a PostIndexUpsertEvent. Drive the real
        // outbox -> RabbitMQ -> consumer -> Elasticsearch path so the search is reached through the
        // production indexing flow rather than a direct document write.
        // The scheduled poll is pushed past the suite runtime, so this manual publish is the only
        // producer; draining first discards any stray delivery before observing the two upserts.
        drainIndexSyncQueue();
        outboxPublisherService.publishDueEvents();
        consumeIndexSyncMessage();
        consumeIndexSyncMessage();
        elasticsearchOperations.indexOps(PostDocument.class).refresh();

        ResponseEntity<Map> response = getWithAuth("/api/v1/posts/search?q=sunset", viewer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<?, ?>> content = contentOf(response);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id")).isEqualTo(visiblePostId.toString());
    }

    private void consumeIndexSyncMessage() throws java.io.IOException {
        Message message =
                rabbitTemplate.receive(RabbitMqTopologyConfig.POST_INDEX_SYNC_QUEUE, 5000L);
        assertThat(message).isNotNull();
        postIndexSyncConsumer.consume(message, Mockito.mock(Channel.class));
    }

    private void drainIndexSyncQueue() {
        while (rabbitTemplate.receive(RabbitMqTopologyConfig.POST_INDEX_SYNC_QUEUE) != null) {
            // Discard residual deliveries so the test observes only its own upsert messages.
        }
    }

    @Test
    @Order(11)
    void searchPosts_elasticsearchDown_returnsEmptyPage() {
        TestUser viewer = registerUser("downtime_viewer");

        elasticsearch.stop();

        ResponseEntity<Map> response = getWithAuth("/api/v1/posts/search?q=anything", viewer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).isEmpty();
    }

    @Test
    @Order(12)
    void getFeed_noFollows_returnsEmptyPage() {
        TestUser viewer = registerUser("feed_nofollows_viewer");

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).isEmpty();
    }

    @Test
    @Order(30)
    void getFeed_noFollowsWithMalformedCursor_returns400NotEmptyPage() {
        TestUser viewer = registerUser("feed_nofollow_badcursor");

        ResponseEntity<Map> response = getFeed(viewer, "!!!not-valid!!!", 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(13)
    void getFeed_acceptedFollow_returnsFollowedPosts() {
        TestUser author = registerUser("feed_author");
        TestUser viewer = registerUser("feed_viewer");
        createImagePost(author, "hello feed", insertMediaAsset(author.id(), "image"));
        insertFollow(viewer.id(), author.id(), "accepted");

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).hasSize(1);
    }

    @Test
    @Order(14)
    void getFeed_pendingFollowOnly_returnsEmptyPage() {
        TestUser author = registerUser("feed_pending_author");
        TestUser viewer = registerUser("feed_pending_viewer");
        createImagePost(author, "pending content", insertMediaAsset(author.id(), "image"));
        insertFollow(viewer.id(), author.id(), "pending");

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).isEmpty();
    }

    @Test
    @Order(15)
    void getFeed_viewerBlockedAuthor_excludesAuthorPosts() {
        TestUser author = registerUser("feed_blocked_author");
        TestUser viewer = registerUser("feed_blocker_viewer");
        createImagePost(author, "blocked out", insertMediaAsset(author.id(), "image"));
        insertFollow(viewer.id(), author.id(), "accepted");
        insertBlock(viewer.id(), author.id());

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).isEmpty();
    }

    @Test
    @Order(16)
    void getFeed_authorBlockedViewer_excludesAuthorPosts() {
        TestUser author = registerUser("feed_blocking_author");
        TestUser viewer = registerUser("feed_blocked_viewer");
        createImagePost(author, "reverse blocked", insertMediaAsset(author.id(), "image"));
        insertFollow(viewer.id(), author.id(), "accepted");
        insertBlock(author.id(), viewer.id());

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).isEmpty();
    }

    @Test
    @Order(17)
    void getFeed_softDeletedPost_notIncluded() {
        TestUser author = registerUser("feed_softdel_author");
        TestUser viewer = registerUser("feed_softdel_viewer");
        UUID postId =
                createImagePost(author, "soon deleted", insertMediaAsset(author.id(), "image"));
        insertFollow(viewer.id(), author.id(), "accepted");
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = NOW(), status = 'removed' WHERE id = ?", postId);

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(response)).isEmpty();
    }

    @Test
    @Order(18)
    void getFeed_draftPost_notIncluded() {
        TestUser author = registerUser("feed_draft_author");
        TestUser viewer = registerUser("feed_draft_viewer");
        createImagePost(author, "draft hidden", insertMediaAsset(author.id(), "image"));
        // Revert to draft after creation.
        jdbcTemplate.update("UPDATE posts SET status = 'draft' WHERE user_id = ?", author.id());
        createImagePost(author, "published visible", insertMediaAsset(author.id(), "image"));
        insertFollow(viewer.id(), author.id(), "accepted");

        ResponseEntity<Map> response = getFeed(viewer, null, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Only the published post appears.
        assertThat(contentOf(response)).hasSize(1);
    }

    @Test
    @Order(19)
    void getFeed_pagination_nextPageCursorWorks() {
        TestUser author = registerUser("feed_page_author");
        TestUser viewer = registerUser("feed_page_viewer");
        insertFollow(viewer.id(), author.id(), "accepted");
        createImagePost(author, "post one", insertMediaAsset(author.id(), "image"));
        createImagePost(author, "post two", insertMediaAsset(author.id(), "image"));
        createImagePost(author, "post three", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> firstPage = getFeed(viewer, null, 2);
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(firstPage)).hasSize(2);

        Map<?, ?> pageInfo =
                (Map<?, ?>) ((Map<?, ?>) firstPage.getBody().get("data")).get("pageInfo");
        assertThat((Boolean) pageInfo.get("hasNextPage")).isTrue();
        String endCursor = (String) pageInfo.get("endCursor");
        assertThat(endCursor).isNotNull();

        ResponseEntity<Map> secondPage = getFeed(viewer, endCursor, 2);
        assertThat(secondPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(secondPage)).hasSize(1);
    }

    @Test
    @Order(21)
    void createPost_textType_success() {
        TestUser author = registerUser("text_post_author");

        Map<String, Object> payload = new HashMap<>();
        payload.put("postType", "text");
        payload.put("caption", "Hello text world");

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(countOf("posts")).isEqualTo(1);
        assertThat(countOf("post_media")).isEqualTo(0);
    }

    @Test
    @Order(22)
    void createPost_textType_blankCaption_returnsBadRequest() {
        TestUser author = registerUser("text_blank_author");

        Map<String, Object> payload = new HashMap<>();
        payload.put("postType", "text");
        payload.put("caption", "");

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(23)
    void createPost_textType_withMedia_returnsBadRequest() {
        TestUser author = registerUser("text_media_author");
        UUID mediaId = insertMediaAsset(author.id(), "image");

        Map<String, Object> payload = new HashMap<>();
        payload.put("postType", "text");
        payload.put("caption", "has media");
        payload.put("mediaIds", List.of(mediaId.toString()));

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countOf("posts")).isEqualTo(0);
    }

    @Test
    @Order(20)
    void getFeed_invalidCursor_returnsBadRequest() {
        TestUser author = registerUser("feed_badcursor_author");
        TestUser viewer = registerUser("feed_badcursor_viewer");
        // Cursor decoding only runs when the viewer has at least one eligible follow; without a
        // follow the service short-circuits before decoding and returns an empty 200 page.
        insertFollow(viewer.id(), author.id(), "accepted");

        ResponseEntity<Map> response = getFeed(viewer, "!!!not-valid!!!", 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(24)
    void getFeed_limitZero_returns400() {
        TestUser viewer = registerUser("feed_limitzero_viewer");

        ResponseEntity<Map> response = getFeed(viewer, null, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Order(25)
    void listUserPosts_limitZero_returns400() {
        TestUser viewer = registerUser("userposts_limitzero_viewer");

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/posts/user/" + viewer.id() + "?limit=0", viewer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Order(26)
    void listEditHistory_limitZero_returns400() {
        TestUser author = registerUser("history_limitzero_author");
        UUID postId =
                createImagePost(author, "history probe", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/posts/" + postId + "/history?limit=0", author);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Order(27)
    void searchPosts_limitZero_returns400() {
        TestUser viewer = registerUser("search_limitzero_viewer");

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/posts/search?q=anything&limit=0", viewer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Order(28)
    void listSavedPosts_limitZero_returns400() {
        TestUser viewer = registerUser("saved_limitzero_viewer");

        ResponseEntity<Map> response = getWithAuth("/api/v1/posts/saved?limit=0", viewer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Order(29)
    void listLikers_limitZero_returns400() {
        TestUser author = registerUser("likers_limitzero_author");
        UUID postId =
                createImagePost(author, "likers probe", insertMediaAsset(author.id(), "image"));

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/posts/" + postId + "/likes?limit=0", author);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    private TestUser registerUser(String username) {
        String email = username + "@test.local";
        String password = "S3cur3P@ssword!";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Dev trusts 127.0.0.1 as a proxy, so a unique X-Forwarded-For isolates the per-IP auth
        // rate-limit bucket of every registered user (login allows only 10 attempts per minute).
        headers.set("X-Forwarded-For", uniqueIp());

        Map<String, Object> registerPayload =
                Map.of(
                        "username",
                        username,
                        "email",
                        email,
                        "password",
                        password,
                        "displayName",
                        username);
        ResponseEntity<Map> register =
                rest.exchange(
                        "/api/v1/auth/register",
                        HttpMethod.POST,
                        new HttpEntity<>(registerPayload, headers),
                        Map.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID id =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE username = ?", UUID.class, username);
        // Local login requires a verified email; flip the flag directly — the verification flow
        // itself is covered by AuthControllerIT, not by this suite.
        jdbcTemplate.update(
                "UPDATE user_credentials SET email_verified = TRUE WHERE user_id = ?", id);

        ResponseEntity<Map> login =
                rest.exchange(
                        "/api/v1/auth/login",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("identifier", email, "password", password), headers),
                        Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) login.getBody().get("data");
        return new TestUser(id, (String) data.get("accessToken"));
    }

    private static String uniqueIp() {
        // 10.x.x.x address unique per registered user so rate-limit buckets do not bleed between
        // scenarios (AuthControllerIT precedent).
        java.util.Random rand = new java.util.Random();
        return "10." + rand.nextInt(256) + "." + rand.nextInt(256) + "." + (1 + rand.nextInt(254));
    }

    private HttpHeaders authHeaders(TestUser user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private UUID insertMediaAsset(UUID ownerId, String mediaType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO media_assets (id, user_id, storage_key, cdn_url, media_type,"
                        + " mime_type, file_size) VALUES (?, ?, ?, ?, CAST(? AS media_type), ?,"
                        + " ?)",
                id,
                ownerId,
                "test/" + id,
                "https://cdn.test/" + id,
                mediaType,
                "image".equals(mediaType) ? "image/jpeg" : "video/mp4",
                1024L);
        return id;
    }

    private UUID createImagePost(TestUser author, String caption, UUID... mediaIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caption", caption);
        payload.put("postType", mediaIds.length > 1 ? "carousel" : "image");
        payload.put("mediaIds", Arrays.stream(mediaIds).map(UUID::toString).toList());
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/posts",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, authHeaders(author)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) ((Map<?, ?>) response.getBody().get("data")).get("id"));
    }

    private ResponseEntity<Map> getWithAuth(String url, TestUser user) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders(user)), Map.class);
    }

    private ResponseEntity<Map> getFeed(TestUser viewer, String cursor, int limit) {
        String url =
                "/api/v1/posts/feed?limit=" + limit + (cursor != null ? "&cursor=" + cursor : "");
        return getWithAuth(url, viewer);
    }

    private void insertFollow(UUID followerId, UUID followingId, String status) {
        jdbcTemplate.update(
                "INSERT INTO follows (follower_id, following_id, status)"
                        + " VALUES (?, ?, CAST(? AS follow_status))",
                followerId,
                followingId,
                status);
    }

    private void insertBlock(UUID blockerId, UUID blockedId) {
        jdbcTemplate.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)", blockerId, blockedId);
    }

    private ResponseEntity<Map> transition(TestUser user, UUID postId, String targetStatus) {
        return rest.exchange(
                "/api/v1/posts/" + postId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("targetStatus", targetStatus), authHeaders(user)),
                Map.class);
    }

    private int countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int postCountOf(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT post_count FROM users WHERE id = ?", Integer.class, userId);
    }

    private int postHashtagCount(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_hashtags WHERE post_id = ?", Integer.class, postId);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> contentOf(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return (List<Map<?, ?>>) data.get("content");
    }
}
