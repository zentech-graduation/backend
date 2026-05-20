package com.app.common.outbox.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.app.common.outbox.entity.OutboxEvent;
import com.app.common.outbox.enums.OutboxEventStatus;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Repository
public class OutboxEventRepositoryImpl implements OutboxEventRepositoryCustom {

    private static final ObjectMapper JSON_MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build();

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
                        .addValue("payload", writePayload(event.getPayload()))
                        .addValue("status", event.getStatus().name())
                        .addValue("attemptCount", event.getAttemptCount())
                        .addValue("nextRetryAt", event.getNextRetryAt());

        return jdbcTemplate.queryForObject(INSERT_PENDING_SQL, params, this::mapEvent);
    }

    private OutboxEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return OutboxEvent.builder()
                .id(rs.getObject("id", UUID.class))
                .eventId(rs.getObject("event_id", UUID.class))
                .aggregateType(rs.getString("aggregate_type"))
                .aggregateId(rs.getObject("aggregate_id", UUID.class))
                .eventType(rs.getString("event_type"))
                .routingKey(rs.getString("routing_key"))
                .payload(readPayload(rs.getString("payload")))
                .status(OutboxEventStatus.valueOf(rs.getString("status")))
                .attemptCount(rs.getInt("attempt_count"))
                .nextRetryAt(rs.getObject("next_retry_at", OffsetDateTime.class))
                .lastError(rs.getString("last_error"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .publishedAt(rs.getObject("published_at", OffsetDateTime.class))
                .build();
    }

    private String writePayload(DomainEventEnvelope payload) {
        try {
            return JSON_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Outbox event payload must be JSON serializable", ex);
        }
    }

    private DomainEventEnvelope readPayload(String payload) {
        try {
            return JSON_MAPPER.readValue(payload, DomainEventEnvelope.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored outbox event payload is not readable", ex);
        }
    }
}
