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
				last_error,
				created_at,
				published_at
			""";

    private static final String FIND_PUBLISHABLE_BATCH_SQL =
            """
			SELECT
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
				last_error,
				created_at,
				published_at
			FROM outbox_events
			WHERE status IN ('PENDING', 'PROCESSING')
				AND next_retry_at <= :now
			ORDER BY created_at
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""";

    private static final String MARK_PUBLISHED_SQL =
            """
			UPDATE outbox_events
			SET status = 'PUBLISHED',
				published_at = :publishedAt,
				last_error = NULL
			WHERE id = :id
			""";

    private static final String MARK_FAILED_SQL =
            """
			UPDATE outbox_events
			SET status = 'PENDING',
				attempt_count = :attemptCount,
				next_retry_at = :nextRetryAt,
				last_error = :lastError,
				published_at = NULL
			WHERE id = :id
			""";

    private static final String MARK_DEAD_SQL =
            """
			UPDATE outbox_events
			SET status = 'DEAD',
				attempt_count = :attemptCount,
				next_retry_at = :deadAt,
				last_error = :lastError,
				published_at = NULL
			WHERE id = :id
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
    public List<OutboxEvent> findPublishableBatch(OffsetDateTime now, int batchSize) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("now", now).addValue("batchSize", batchSize);

        return jdbcTemplate.query(FIND_PUBLISHABLE_BATCH_SQL, params, this::mapEvent);
    }

    @Override
    public void markPublished(UUID id, OffsetDateTime publishedAt) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("id", id).addValue("publishedAt", publishedAt);

        jdbcTemplate.update(MARK_PUBLISHED_SQL, params);
    }

    @Override
    public void markFailed(
            UUID id, int attemptCount, OffsetDateTime nextRetryAt, String lastError) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("attemptCount", attemptCount)
                        .addValue("nextRetryAt", nextRetryAt)
                        .addValue("lastError", lastError);

        jdbcTemplate.update(MARK_FAILED_SQL, params);
    }

    @Override
    public void markDead(UUID id, int attemptCount, OffsetDateTime deadAt, String lastError) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("attemptCount", attemptCount)
                        .addValue("deadAt", deadAt)
                        .addValue("lastError", lastError);

        jdbcTemplate.update(MARK_DEAD_SQL, params);
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
                .lastError(rs.getString("last_error"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .publishedAt(rs.getObject("published_at", OffsetDateTime.class))
                .build();
    }
}
