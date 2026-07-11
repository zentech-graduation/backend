package com.app.modules.recommendation.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
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

import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.model.DomainEventEnvelopeJson;
import com.app.modules.mail.service.MailService;
import com.app.modules.recommendation.messaging.RecommendationEventTypes;
import com.rabbitmq.client.Channel;

/**
 * Integration test for the recommendation event-persist path: the consumer receives a real RabbitMQ
 * message body, persists a {@code user_events} row, and enforces inbox idempotency.
 *
 * <p>PostgreSQL is required for the partitioned {@code user_events} table and the inbox {@code
 * processed_messages} dedup; Redis is present for context-cache parity with the rest of the app;
 * RabbitMQ is not strictly required because the consumer is invoked directly with a parsed message
 * body (matching {@code PostIndexSyncConsumerIT}).
 */
@SpringBootTest(
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "app.outbox.publisher.enabled=false",
            "app.recommendation.consumer.enabled=true",
            "app.messaging.consumer.max-attempts=1",
            "spring.rabbitmq.publisher-confirm-type=correlated",
            "spring.rabbitmq.publisher-returns=true",
            "spring.rabbitmq.template.mandatory=true"
        })
@Testcontainers
class RecEventFlowIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbit =
            new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-alpine"))
                    .withExposedPorts(5672);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.rabbitmq.host", rabbit::getHost);
        r.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
        r.add("JWT_SECRET", () -> "rec-flow-it-secret-32-chars-min-length!!!!!");
        r.add("JWT_ISSUER", () -> "https://rec-flow-it.test.local");
        r.add("JWT_AUDIENCE", () -> "rec-flow-it");
        r.add("ACCESS_TOKEN_TTL", () -> 900L);
        r.add("REFRESH_TOKEN_TTL", () -> 3600L);
        r.add("APP_BASE_URL", () -> "http://localhost:8080");
        r.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        r.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        r.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        r.add("MAIL_FROM_NAME", () -> "App Rec Flow IT");
        r.add("MAIL_APP_NAME", () -> "App");
        r.add("FRONTEND_BASE_URL", () -> "http://localhost:3000");
        r.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        r.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        r.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @MockitoBean private MailService mailService;

    @Autowired private RecEventPersistConsumer consumer;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE processed_messages");
        jdbcTemplate.execute("DELETE FROM user_events");
        jdbcTemplate.execute("DELETE FROM impressions");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    void consumeInteractionEvent_persistsUserEventsRow() throws Exception {
        UUID userId = insertUser();
        UUID postId = UUID.randomUUID();
        DomainEventEnvelope event = interactionEnvelope(UUID.randomUUID(), userId, postId);
        Channel channel = mock(Channel.class);

        consumer.consume(buildMessage(event), channel);

        verify(channel).basicAck(0L, false);
        Integer rows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_events WHERE user_id = ? AND event_type = 'post_like'",
                        Integer.class,
                        userId);
        assertThat(rows).isEqualTo(1);
        // The behavioral timestamp is the envelope's occurredAt, not consumption time.
        OffsetDateTime createdAt =
                jdbcTemplate.queryForObject(
                        "SELECT created_at FROM user_events WHERE user_id = ?",
                        OffsetDateTime.class,
                        userId);
        assertThat(createdAt.toInstant()).isEqualTo(event.occurredAt().toInstant());
    }

    @Test
    void consumeDuplicateInteraction_persistsOnlyOneRow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = insertUser();
        UUID postId = UUID.randomUUID();
        Channel channel = mock(Channel.class);

        consumer.consume(buildMessage(interactionEnvelope(eventId, userId, postId)), channel);
        consumer.consume(buildMessage(interactionEnvelope(eventId, userId, postId)), channel);

        Integer processedRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_messages WHERE consumer_name = ? AND event_id = ?",
                        Integer.class,
                        RecEventPersistConsumer.CONSUMER_NAME,
                        eventId);
        assertThat(processedRows).isEqualTo(1);
        Integer eventRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_events WHERE user_id = ?",
                        Integer.class,
                        userId);
        assertThat(eventRows).isEqualTo(1);
    }

    @Test
    void consumeImpressionBatch_persistsImpressionRows() throws Exception {
        UUID userId = insertUser();
        UUID postId = UUID.randomUUID();
        UUID clientEventId = UUID.randomUUID();
        DomainEventEnvelope event =
                impressionBatchEnvelope(UUID.randomUUID(), userId, postId, clientEventId);
        Channel channel = mock(Channel.class);

        consumer.consume(buildMessage(event), channel);

        verify(channel).basicAck(0L, false);
        // The impression item lands in impressions only; the post_view item lands in user_events
        // only, so it feeds CF training without inflating the CTR denominator.
        Integer impressionRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM impressions WHERE user_id = ? AND post_id = ?",
                        Integer.class,
                        userId,
                        postId);
        assertThat(impressionRows).isEqualTo(1);
        Integer postViewRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_events"
                                + " WHERE user_id = ? AND event_type = 'post_view'",
                        Integer.class,
                        userId);
        assertThat(postViewRows).isEqualTo(1);
        Integer postViewImpressionRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM impressions WHERE user_id = ?",
                        Integer.class,
                        userId);
        assertThat(postViewImpressionRows).isEqualTo(1);
    }

    @Test
    void consumeImpressionBatch_replayDoesNotDuplicateRows() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = insertUser();
        UUID postId = UUID.randomUUID();
        UUID clientEventId = UUID.randomUUID();
        Channel channel = mock(Channel.class);

        consumer.consume(
                buildMessage(impressionBatchEnvelope(eventId, userId, postId, clientEventId)),
                channel);
        // Replay the same envelope; inbox dedup suppresses the handler entirely.
        consumer.consume(
                buildMessage(impressionBatchEnvelope(eventId, userId, postId, clientEventId)),
                channel);

        Integer rows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM impressions WHERE user_id = ? AND post_id = ?",
                        Integer.class,
                        userId,
                        postId);
        assertThat(rows).isEqualTo(1);
        Integer postViewRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_events"
                                + " WHERE user_id = ? AND event_type = 'post_view'",
                        Integer.class,
                        userId);
        assertThat(postViewRows).isEqualTo(1);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        // users has no password_hash column (credentials live in user_credentials); only the
        // NOT NULL defaults plus a unique username/email are needed for an FK target.
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role, status, is_private,"
                        + " is_verified, follower_count, following_count, post_count)"
                        + " VALUES (?, ?, ?, 'user', 'active', false, false, 0, 0, 0)",
                userId,
                "rec_" + userId.toString().substring(0, 8),
                userId.toString().substring(0, 8) + "@test.local");
        return userId;
    }

    private Message buildMessage(DomainEventEnvelope event) {
        return MessageBuilder.withBody(
                        DomainEventEnvelopeJson.write(event).getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private DomainEventEnvelope interactionEnvelope(UUID eventId, UUID userId, UUID postId) {
        // Millisecond precision survives both the JSON round trip and the timestamptz column
        // (microsecond precision) intact, so the created_at equality assertion is exact.
        return new DomainEventEnvelope(
                eventId,
                RecommendationEventTypes.REC_INTERACTION_RECORDED_V1,
                OffsetDateTime.parse("2026-07-10T08:15:30.123Z"),
                userId,
                "post",
                postId,
                Map.of(
                        "eventType",
                        "post_like",
                        "entityType",
                        "post",
                        "entityId",
                        postId.toString(),
                        "targetUserId",
                        userId.toString()));
    }

    private DomainEventEnvelope impressionBatchEnvelope(
            UUID eventId, UUID userId, UUID postId, UUID clientEventId) {
        // Timestamps derive from the eventId so a replayed envelope is byte-for-byte identical
        // and the deterministic row ids actually collide on replay.
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-07-10T08:15:30.123Z");
        Map<String, Object> impressionItem =
                Map.of(
                        "clientEventId",
                        clientEventId.toString(),
                        "type",
                        "impression",
                        "postId",
                        postId.toString(),
                        "position",
                        0,
                        "source",
                        "cf",
                        "occurredAt",
                        occurredAt.toString());
        // Deterministic ids derived from the batch's clientEventId keep replays identical.
        Map<String, Object> postViewItem =
                Map.of(
                        "clientEventId",
                        UUID.nameUUIDFromBytes(clientEventId.toString().getBytes()).toString(),
                        "type",
                        "post_view",
                        "postId",
                        UUID.nameUUIDFromBytes(postId.toString().getBytes()).toString(),
                        "position",
                        1,
                        "source",
                        "trending",
                        "occurredAt",
                        occurredAt.toString());
        return new DomainEventEnvelope(
                eventId,
                RecommendationEventTypes.REC_IMPRESSION_BATCH_V1,
                occurredAt,
                userId,
                "user",
                userId,
                Map.of(
                        "sessionId",
                                UUID.nameUUIDFromBytes(eventId.toString().getBytes()).toString(),
                        "platform", "web",
                        "requestId",
                                UUID.nameUUIDFromBytes(userId.toString().getBytes()).toString(),
                        "items", List.of(impressionItem, postViewItem)));
    }
}
