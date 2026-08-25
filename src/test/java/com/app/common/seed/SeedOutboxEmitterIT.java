package com.app.common.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.common.seed.loader.SeedContent;
import com.app.common.seed.loader.SeedDataLoader;
import com.app.common.seed.outbox.SeedOutboxEmitter;
import com.app.common.seed.reset.SeedResetService;
import com.app.common.seed.time.SeedTimeline;
import com.app.common.seed.writer.CommentSeedWriter;
import com.app.common.seed.writer.EngagementSeedWriter;
import com.app.common.seed.writer.MediaSeedWriter;
import com.app.common.seed.writer.PostSeedWriter;
import com.app.common.seed.writer.UserSeedWriter;
import com.app.modules.mail.service.MailService;

/**
 * Proves {@link SeedOutboxEmitter} against a real Flyway-migrated schema, chained after every
 * domain writer whose rows it reads back, the same way Task 8's {@code SeedRunner} will chain it
 * last.
 *
 * <p>This test only proves the {@code outbox_events} rows themselves are correctly shaped and
 * counted — it disables the outbox publisher and excludes RabbitMQ autoconfiguration, matching
 * every other seed writer IT's pattern. The live-stack drain through the real publisher and real
 * Elasticsearch/Gorse consumers (the mandatory envelope-correctness gate) was proven separately
 * against the local docker-compose stack; see the Task 9 report for that raw evidence.
 */
@SpringBootTest(
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
        })
@Testcontainers
class SeedOutboxEmitterIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("JWT_SECRET", () -> "seed-outbox-emitter-it-secret-32-characters!!");
        registry.add("JWT_ISSUER", () -> "https://seedoutboxemitter.it.local");
        registry.add("JWT_AUDIENCE", () -> "App");
        registry.add("ACCESS_TOKEN_TTL", () -> 900L);
        registry.add("REFRESH_TOKEN_TTL", () -> 3600L);
        registry.add("APP_BASE_URL", () -> "http://localhost:8080");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        registry.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        registry.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        registry.add("MAIL_FROM_NAME", () -> "App IT");
        registry.add("MAIL_APP_NAME", () -> "App");
        registry.add("FRONTEND_BASE_URL", () -> "http://localhost:3000");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        registry.add(
                "spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
        registry.add("app.outbox.publisher.enabled", () -> false);
        registry.add("app.hashtag.seed.enabled", () -> false);
        registry.add("app.post.seed.enabled", () -> false);
        registry.add("app.stats.enabled", () -> false);
    }

    @MockitoBean private MailService mailService;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SeedResetService seedResetService;
    @Autowired private UserSeedWriter userSeedWriter;
    @Autowired private MediaSeedWriter mediaSeedWriter;
    @Autowired private PostSeedWriter postSeedWriter;
    @Autowired private CommentSeedWriter commentSeedWriter;
    @Autowired private EngagementSeedWriter engagementSeedWriter;
    @Autowired private SeedOutboxEmitter seedOutboxEmitter;

    @Test
    void emitProofSlice_writesExactlyOneEventOfEachKind() {
        seedDomainWriters();

        seedOutboxEmitter.emitProofSlice();

        Map<String, Integer> countByType = countOutboxEventsByType();
        assertThat(countByType.get("post.index.upsert.v1")).isEqualTo(1);
        assertThat(countByType.get("hashtag.index.upsert.v1")).isEqualTo(1);
        assertThat(countByType.get("post.liked.v1")).isEqualTo(1);
        assertThat(countByType.get("post.saved.v1")).isEqualTo(1);
        assertThat(countByType.get("comment.created.v1")).isEqualTo(1);
        assertThat(countByType.getOrDefault("post.viewed.v1", 0)).isZero();

        Integer total =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(total).isEqualTo(5);
    }

    @Test
    void emitFullVolume_writesOneEventPerRowAndNeverEmitsPostViewed() {
        seedDomainWriters();

        Integer publishedPostCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM posts WHERE status = 'published'::post_status",
                        Integer.class);
        Integer hashtagCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM hashtags", Integer.class);
        Integer likeCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_likes", Integer.class);
        Integer saveCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_saves", Integer.class);
        Integer commentCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM comments WHERE deleted_at IS NULL", Integer.class);

        SeedOutboxEmitter.EmissionCounts counts = seedOutboxEmitter.emitFullVolume();

        assertThat(counts.postIndex()).isEqualTo(publishedPostCount);
        assertThat(counts.hashtagIndex()).isEqualTo(hashtagCount);
        assertThat(counts.likes()).isEqualTo(likeCount);
        assertThat(counts.saves()).isEqualTo(saveCount);
        assertThat(counts.comments()).isEqualTo(commentCount);

        Map<String, Integer> countByType = countOutboxEventsByType();
        assertThat(countByType.get("post.index.upsert.v1")).isEqualTo(publishedPostCount);
        assertThat(countByType.get("hashtag.index.upsert.v1")).isEqualTo(hashtagCount);
        assertThat(countByType.get("post.liked.v1")).isEqualTo(likeCount);
        assertThat(countByType.get("post.saved.v1")).isEqualTo(saveCount);
        assertThat(countByType.get("comment.created.v1")).isEqualTo(commentCount);
        assertThat(countByType).doesNotContainKey("post.viewed.v1");

        Integer total =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(total)
                .isEqualTo(
                        publishedPostCount + hashtagCount + likeCount + saveCount + commentCount);

        // Every row must still be PENDING: the outbox publisher is disabled in this test, so
        // nothing has drained yet — proves emission alone, not the live-stack drain.
        Integer pendingCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'",
                        Integer.class);
        assertThat(pendingCount).isEqualTo(total);
    }

    @Test
    void emitPostIndexPayload_matchesPostIndexUpsertEventShape() {
        seedDomainWriters();

        seedOutboxEmitter.emitProofSlice();

        String payloadJson =
                jdbcTemplate.queryForObject(
                        "SELECT payload::text FROM outbox_events WHERE event_type ="
                                + " 'post.index.upsert.v1'",
                        String.class);
        assertThat(payloadJson).contains("\"postId\"");
        assertThat(payloadJson).contains("\"userId\"");
        assertThat(payloadJson).contains("\"hashtagIds\"");
        assertThat(payloadJson).contains("\"createdAt\"");
    }

    private void seedDomainWriters() {
        seedResetService.reset();
        SeedContent content = new SeedDataLoader().load();
        SeedTimeline timeline = new SeedTimeline(20260825L, Instant.parse("2026-08-25T00:00:00Z"));

        Map<String, UUID> usersByUsername = userSeedWriter.write(content, timeline);
        Map<String, UUID> mediaByCompositeKey = mediaSeedWriter.write(content, usersByUsername);
        Map<String, UUID> postIdBySeedId =
                postSeedWriter.write(content, usersByUsername, mediaByCompositeKey, timeline);
        List<UUID> commentIds =
                commentSeedWriter.write(content, usersByUsername, postIdBySeedId, timeline);
        engagementSeedWriter.write(content, usersByUsername, postIdBySeedId, commentIds, timeline);
    }

    private Map<String, Integer> countOutboxEventsByType() {
        return jdbcTemplate
                .query(
                        "SELECT event_type, COUNT(*) AS cnt FROM outbox_events GROUP BY event_type",
                        (rs, rowNum) -> Map.entry(rs.getString("event_type"), rs.getInt("cnt")))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
