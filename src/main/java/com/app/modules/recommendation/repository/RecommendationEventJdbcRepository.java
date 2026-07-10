package com.app.modules.recommendation.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.app.modules.recommendation.dto.ImpressionBatchEvent;

/**
 * Batch-inserts behavioral events into the append-only, range-partitioned {@code user_events} and
 * {@code impressions} tables.
 *
 * <p>Uses {@link NamedParameterJdbcTemplate} rather than JPA because both tables are partitioned
 * and append-only: no entity lifecycle, no dirty checking, and {@code user_events} has no
 * JPA-friendly unique constraint to reconcile. Idempotency is enforced one level up by the inbox
 * {@code processed_messages} table (per envelope) plus deterministic row ids for impression items
 * (per batch item), so a replayed batch after partial failure cannot duplicate rows.
 */
@Repository
public class RecommendationEventJdbcRepository {

    private static final String INSERT_USER_EVENT_SQL =
            """
			INSERT INTO user_events (
				id, user_id, session_id, event_type, entity_type, entity_id,
				metadata, platform, created_at
			) VALUES (
				:id, :userId, :sessionId, CAST(:eventType AS event_type), :entityType, :entityId,
				CAST(:metadata AS jsonb), :platform, :createdAt
			)
			""";

    private static final String INSERT_IMPRESSION_SQL =
            """
			INSERT INTO impressions (
				id, user_id, post_id, source, position, request_id, blend_score, created_at
			) VALUES (
				:id, :userId, :postId, :source, :position, :requestId, :blendScore, :createdAt
			)
			ON CONFLICT DO NOTHING
			""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecommendationEventJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts one row into {@code user_events}; idempotency is the caller's (inbox) responsibility.
     */
    public void insertUserEvent(
            UUID id,
            UUID userId,
            UUID sessionId,
            String eventType,
            String entityType,
            UUID entityId,
            String metadataJson,
            String platform,
            OffsetDateTime createdAt) {
        jdbcTemplate.update(
                INSERT_USER_EVENT_SQL,
                userEventParams(
                        id,
                        userId,
                        sessionId,
                        eventType,
                        entityType,
                        entityId,
                        metadataJson,
                        platform,
                        createdAt));
    }

    /**
     * Inserts one interaction-derived {@code user_events} row for an impression-batch post_view
     * item.
     */
    public void insertUserEvent(
            UUID id,
            UUID userId,
            UUID sessionId,
            String eventType,
            String entityType,
            UUID entityId,
            String platform,
            OffsetDateTime createdAt) {
        insertUserEvent(
                id, userId, sessionId, eventType, entityType, entityId, null, platform, createdAt);
    }

    /** Inserts every impression item in one batch; duplicate ids are silently skipped. */
    public void insertImpressions(UUID userId, ImpressionBatchEvent batch) {
        OffsetDateTime defaultOccurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        MapSqlParameterSource[] params =
                batch.items().stream()
                        .map(
                                item -> {
                                    OffsetDateTime occurredAt =
                                            item.occurredAt() == null
                                                    ? defaultOccurredAt
                                                    : item.occurredAt();
                                    // Deterministic id from the client event id so a replayed batch
                                    // after partial failure never duplicates rows.
                                    UUID rowId = deterministicId(item.clientEventId());
                                    return new MapSqlParameterSource()
                                            .addValue("id", rowId)
                                            .addValue("userId", userId)
                                            .addValue("postId", item.postId())
                                            .addValue("source", item.source())
                                            .addValue("position", item.position())
                                            .addValue("requestId", batch.requestId())
                                            .addValue(
                                                    "blendScore", (BigDecimal) null, Types.NUMERIC)
                                            .addValue(
                                                    "createdAt",
                                                    Timestamp.from(occurredAt.toInstant()));
                                })
                        .toArray(MapSqlParameterSource[]::new);
        if (params.length == 0) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_IMPRESSION_SQL, params);
    }

    private MapSqlParameterSource userEventParams(
            UUID id,
            UUID userId,
            UUID sessionId,
            String eventType,
            String entityType,
            UUID entityId,
            String metadataJson,
            String platform,
            OffsetDateTime createdAt) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("sessionId", sessionId)
                .addValue("eventType", eventType)
                .addValue("entityType", entityType)
                .addValue("entityId", entityId)
                .addValue("metadata", metadataJson, Types.OTHER)
                .addValue("platform", platform)
                .addValue("createdAt", Timestamp.from(createdAt.toInstant()));
    }

    /** Type-3 (name-based with random namespace) UUID for deterministic impression row ids. */
    public static UUID deterministicId(UUID clientEventId) {
        return UUID.nameUUIDFromBytes(asOrderedBytes(clientEventId));
    }

    private static byte[] asOrderedBytes(UUID uuid) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
