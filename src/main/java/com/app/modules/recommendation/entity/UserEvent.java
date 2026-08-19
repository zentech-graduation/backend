package com.app.modules.recommendation.entity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.app.modules.recommendation.converter.UserEventTypeConverter;
import com.app.modules.recommendation.enums.UserEventType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps one append-only behavioural event to the {@code user_events} table.
 *
 * <p>Read-only. Rows are inserted by {@code UserEventRecorder} through plain JDBC on a thread of
 * its own, deliberately outside any persistence context, so nothing here is ever used to write.
 * {@code @Immutable} states that: an event is a record of something that already happened and
 * correcting one by editing it would destroy the evidence it exists to preserve.
 */
@Entity
@Immutable
@Table(name = "user_events")
@IdClass(UserEventId.class)
@Getter
@Setter
@NoArgsConstructor
public class UserEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Convert(converter = UserEventTypeConverter.class)
    @Column(
            name = "event_type",
            nullable = false,
            updatable = false,
            columnDefinition = "event_type")
    private UserEventType eventType;

    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata;

    @Column(name = "platform", length = 10, updatable = false)
    private String platform;
}
