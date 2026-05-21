package com.app.common.inbox.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.app.common.inbox.entity.ProcessedMessage;

@Repository
public class ProcessedMessageRepositoryImpl implements ProcessedMessageRepositoryCustom {

    private static final String INSERT_IF_ABSENT_SQL =
            """
			INSERT INTO processed_messages (
				consumer_name,
				event_id,
				event_type
			)
			VALUES (
				:consumerName,
				:eventId,
				:eventType
			)
			ON CONFLICT (consumer_name, event_id) DO NOTHING
			RETURNING
				id,
				consumer_name,
				event_id,
				event_type,
				processed_at
			""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProcessedMessageRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProcessedMessage> insertIfAbsent(
            String consumerName, UUID eventId, String eventType) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("consumerName", consumerName)
                        .addValue("eventId", eventId)
                        .addValue("eventType", eventType);

        List<ProcessedMessage> messages =
                jdbcTemplate.query(INSERT_IF_ABSENT_SQL, params, this::mapMessage);
        return messages.stream().findFirst();
    }

    private ProcessedMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return ProcessedMessage.builder()
                .id(rs.getObject("id", UUID.class))
                .consumerName(rs.getString("consumer_name"))
                .eventId(rs.getObject("event_id", UUID.class))
                .eventType(rs.getString("event_type"))
                .processedAt(rs.getObject("processed_at", OffsetDateTime.class))
                .build();
    }
}
