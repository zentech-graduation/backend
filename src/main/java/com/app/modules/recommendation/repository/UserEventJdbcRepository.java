package com.app.modules.recommendation.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Append-only writer for the partitioned {@code user_events} behavioral event store.
 *
 * <p>Plain JDBC instead of a JPA entity: the composite primary key includes the partition key and
 * the {@code event_type} column is a PostgreSQL enum, neither of which maps cleanly through
 * Hibernate. Rows are never updated or deleted (canonical event log contract).
 */
@Repository
public class UserEventJdbcRepository {

    private static final String INSERT_SQL =
            "INSERT INTO user_events (id, user_id, event_type, entity_type, entity_id, created_at)"
                    + " VALUES (?, ?, ?::event_type, ?, ?, ?)"
                    + " ON CONFLICT (id, created_at) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    public UserEventJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts one behavioral event, silently skipping duplicates.
     *
     * <p>Callers pass the domain event's id and occurrence time so the insert is deterministic
     * and therefore idempotent across message redeliveries.
     *
     * @param id domain event id, reused as the row id
     * @param userId acting user
     * @param eventType value of the PostgreSQL {@code event_type} enum, e.g. {@code post_like}
     * @param entityType entity kind the event points at, e.g. {@code post}
     * @param entityId id of the target entity
     * @param createdAt original event occurrence time (also the partition key)
     */
    public void insertIgnoreDuplicate(
            UUID id,
            UUID userId,
            String eventType,
            String entityType,
            UUID entityId,
            OffsetDateTime createdAt) {
        jdbcTemplate.update(INSERT_SQL, id, userId, eventType, entityType, entityId, createdAt);
    }
}
