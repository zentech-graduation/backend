package com.app.modules.recommendation.repository;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
 * {@code processed_messages} table (per envelope) plus deterministic row ids for batch-derived rows
 * (per item), so a replayed batch after partial failure cannot duplicate rows.
 */
@Repository
public class RecommendationEventJdbcRepository {

    private static final String IMPRESSION_ITEM_TYPE = "impression";
    private static final String POST_VIEW_EVENT_TYPE = "post_view";

    private static final String INSERT_USER_EVENT_SQL =
            """
			INSERT INTO user_events (
				id, user_id, session_id, event_type, entity_type, entity_id,
				metadata, platform, created_at
			) VALUES (
				:id, :userId, :sessionId, CAST(:eventType AS event_type), :entityType, :entityId,
				CAST(:metadata AS jsonb), :platform, :createdAt
			)
			ON CONFLICT DO NOTHING
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
     * Inserts one row into {@code user_events}; idempotency is the caller's (inbox) responsibility,
     * with the {@code ON CONFLICT} clause as defense in depth for deterministic ids.
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

    /** Convenience overload for interaction events, which carry no metadata payload. */
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

    /**
     * Inserts the {@code impression} items of the batch; duplicate ids are silently skipped.
     *
     * <p>{@code post_view} items are excluded: they record a click-through, not a display, and
     * counting them here would inflate the CTR denominator. They land in {@code user_events} via
     * {@link #insertPostViewUserEvents} instead.
     */
    public void insertImpressions(UUID userId, ImpressionBatchEvent batch) {
        OffsetDateTime defaultOccurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        MapSqlParameterSource[] params =
                batch.items().stream()
                        .filter(item -> IMPRESSION_ITEM_TYPE.equals(item.type()))
                        .map(
                                item -> {
                                    // Deterministic id from the client event id so a replayed batch
                                    // after partial failure never duplicates rows.
                                    UUID rowId = deterministicImpressionId(item.clientEventId());
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
                                                    Timestamp.from(
                                                            occurredAtOrDefault(
                                                                            item, defaultOccurredAt)
                                                                    .toInstant()));
                                })
                        .toArray(MapSqlParameterSource[]::new);
        if (params.length == 0) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_IMPRESSION_SQL, params);
    }

    /**
     * Inserts one {@code user_events} row per {@code post_view} item of the batch, so client-side
     * views feed collaborative-filter training alongside server-side interactions.
     *
     * <p>Row ids and timestamps are deterministic per item, so a replayed batch after partial
     * failure never duplicates rows ({@code ON CONFLICT DO NOTHING} on the composite PK).
     */
    public void insertPostViewUserEvents(UUID userId, ImpressionBatchEvent batch) {
        OffsetDateTime defaultOccurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<MapSqlParameterSource> params =
                batch.items().stream()
                        .filter(item -> POST_VIEW_EVENT_TYPE.equals(item.type()))
                        .map(
                                item ->
                                        userEventParams(
                                                deterministicUserEventId(item.clientEventId()),
                                                userId,
                                                batch.sessionId(),
                                                POST_VIEW_EVENT_TYPE,
                                                "post",
                                                item.postId(),
                                                null,
                                                batch.platform(),
                                                occurredAtOrDefault(item, defaultOccurredAt)))
                        .toList();
        if (params.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_USER_EVENT_SQL, params.toArray(MapSqlParameterSource[]::new));
    }

    private static OffsetDateTime occurredAtOrDefault(
            ImpressionBatchEvent.Item item, OffsetDateTime defaultOccurredAt) {
        return item.occurredAt() == null ? defaultOccurredAt : item.occurredAt();
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

    /** Type-3 (MD5 name-based) UUID for deterministic impression row ids. */
    public static UUID deterministicImpressionId(UUID clientEventId) {
        return UUID.nameUUIDFromBytes(asOrderedBytes(clientEventId));
    }

    /**
     * Type-3 (MD5 name-based) UUID for deterministic {@code user_events} row ids, prefixed so the
     * same client event id never collides with its impression row id.
     */
    public static UUID deterministicUserEventId(UUID clientEventId) {
        byte[] prefix = "ue:".getBytes(StandardCharsets.UTF_8);
        byte[] uuidBytes = asOrderedBytes(clientEventId);
        byte[] combined = new byte[prefix.length + uuidBytes.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(uuidBytes, 0, combined, prefix.length, uuidBytes.length);
        return UUID.nameUUIDFromBytes(combined);
    }

    private static byte[] asOrderedBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
