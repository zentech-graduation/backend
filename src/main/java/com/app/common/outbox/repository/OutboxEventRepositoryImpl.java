package com.app.common.outbox.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.enums.OutboxEventStatus;
import com.app.common.outbox.model.DomainEventEnvelopeJson;

@Repository
public class OutboxEventRepositoryImpl implements OutboxEventRepositoryCustom {

    private static final String INSERT_PENDING_SQL =
            """
			INSERT INTO outbox_events (
				event_id,
				aggregate_type,
				aggregate_id,
				event_type,
				routing_key,
				payload,
				status,
				attempt_count,
				next_retry_at
			)
			VALUES (
				:eventId,
				:aggregateType,
				:aggregateId,
				:eventType,
				:routingKey,
				CAST(:payload AS jsonb),
				:status,
				:attemptCount,
				:nextRetryAt
			)
			RETURNING
				id,
				event_id,
				aggregate_type,
				aggregate_id,
				event_type,
				routing_key,
				payload,
				status,
				attempt_count,
				next_retry_at,
				claim_id,
				claimed_at,
				claimed_until,
				last_error,
				created_at,
				published_at
			""";

    private static final String CLAIM_PUBLISHABLE_BATCH_SQL =
            """
			WITH picked AS (
				SELECT id
				FROM outbox_events
				WHERE status IN ('PENDING', 'PROCESSING')
					AND next_retry_at <= :now
				ORDER BY created_at
				LIMIT :batchSize
				FOR UPDATE SKIP LOCKED
			)
			UPDATE outbox_events event
			SET status = 'PROCESSING',
				claim_id = gen_random_uuid(),
				claimed_at = :claimedAt,
				claimed_until = :claimedUntil,
				next_retry_at = :claimedUntil
			FROM picked
			WHERE event.id = picked.id
			RETURNING
				event.id,
				event.event_id,
				event.aggregate_type,
				event.aggregate_id,
				event.event_type,
				event.routing_key,
				event.payload,
				event.status,
				event.attempt_count,
				event.next_retry_at,
				event.claim_id,
				event.claimed_at,
				event.claimed_until,
				event.last_error,
				event.created_at,
				event.published_at
			""";

    private static final String MARK_PUBLISHED_SQL =
            """
			UPDATE outbox_events
			SET status = 'PUBLISHED',
				published_at = :publishedAt,
				last_error = NULL
			WHERE id = :id
				AND event_id = :eventId
				AND claim_id = :claimId
				AND status = 'PROCESSING'
			""";

    private static final String MARK_FAILED_SQL =
            """
			UPDATE outbox_events
			SET status = 'PENDING',
				attempt_count = :attemptCount,
				next_retry_at = :nextRetryAt,
				last_error = :lastError,
				published_at = NULL,
				claim_id = NULL,
				claimed_at = NULL,
				claimed_until = NULL
			WHERE id = :id
				AND event_id = :eventId
				AND claim_id = :claimId
				AND status = 'PROCESSING'
			""";

    private static final String MARK_DEAD_SQL =
            """
			UPDATE outbox_events
			SET status = 'DEAD',
				attempt_count = :attemptCount,
				next_retry_at = :deadAt,
				last_error = :lastError,
				published_at = NULL,
				claim_id = NULL,
				claimed_at = NULL,
				claimed_until = NULL
			WHERE id = :id
				AND event_id = :eventId
				AND claim_id = :claimId
				AND status = 'PROCESSING'
			""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OutboxEventRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OutboxEvent insertPending(OutboxEvent event) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("eventId", event.getEventId())
                        .addValue("aggregateType", event.getAggregateType())
                        .addValue("aggregateId", event.getAggregateId())
                        .addValue("eventType", event.getEventType())
                        .addValue("routingKey", event.getRoutingKey())
                        .addValue("payload", DomainEventEnvelopeJson.write(event.getPayload()))
                        .addValue("status", event.getStatus().name())
                        .addValue("attemptCount", event.getAttemptCount())
                        .addValue("nextRetryAt", event.getNextRetryAt());

        return jdbcTemplate.queryForObject(INSERT_PENDING_SQL, params, this::mapEvent);
    }

    @Override
    public List<OutboxEvent> claimPublishableBatch(
            OffsetDateTime now,
            OffsetDateTime claimedAt,
            OffsetDateTime claimedUntil,
            int batchSize) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("now", now)
                        .addValue("claimedAt", claimedAt)
                        .addValue("claimedUntil", claimedUntil)
                        .addValue("batchSize", batchSize);

        return jdbcTemplate.query(CLAIM_PUBLISHABLE_BATCH_SQL, params, this::mapEvent);
    }

    @Override
    public boolean markPublished(UUID id, UUID eventId, UUID claimId, OffsetDateTime publishedAt) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("eventId", eventId)
                        .addValue("claimId", claimId)
                        .addValue("publishedAt", publishedAt);

        return jdbcTemplate.update(MARK_PUBLISHED_SQL, params) == 1;
    }

    @Override
    public boolean markFailed(
            UUID id,
            UUID eventId,
            UUID claimId,
            int attemptCount,
            OffsetDateTime nextRetryAt,
            String lastError) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("eventId", eventId)
                        .addValue("claimId", claimId)
                        .addValue("attemptCount", attemptCount)
                        .addValue("nextRetryAt", nextRetryAt)
                        .addValue("lastError", lastError);

        return jdbcTemplate.update(MARK_FAILED_SQL, params) == 1;
    }

    @Override
    public boolean markDead(
            UUID id,
            UUID eventId,
            UUID claimId,
            int attemptCount,
            OffsetDateTime deadAt,
            String lastError) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("eventId", eventId)
                        .addValue("claimId", claimId)
                        .addValue("attemptCount", attemptCount)
                        .addValue("deadAt", deadAt)
                        .addValue("lastError", lastError);

        return jdbcTemplate.update(MARK_DEAD_SQL, params) == 1;
    }

    private OutboxEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return OutboxEvent.builder()
                .id(rs.getObject("id", UUID.class))
                .eventId(rs.getObject("event_id", UUID.class))
                .aggregateType(rs.getString("aggregate_type"))
                .aggregateId(rs.getObject("aggregate_id", UUID.class))
                .eventType(rs.getString("event_type"))
                .routingKey(rs.getString("routing_key"))
                .payload(DomainEventEnvelopeJson.read(rs.getString("payload")))
                .status(OutboxEventStatus.valueOf(rs.getString("status")))
                .attemptCount(rs.getInt("attempt_count"))
                .nextRetryAt(rs.getObject("next_retry_at", OffsetDateTime.class))
                .claimId(rs.getObject("claim_id", UUID.class))
                .claimedAt(rs.getObject("claimed_at", OffsetDateTime.class))
                .claimedUntil(rs.getObject("claimed_until", OffsetDateTime.class))
                .lastError(rs.getString("last_error"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .publishedAt(rs.getObject("published_at", OffsetDateTime.class))
                .build();
    }
}
