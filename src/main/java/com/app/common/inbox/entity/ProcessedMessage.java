package com.app.common.inbox.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Consumer-side idempotency record for a successfully processed message. */
@Entity
@Table(
        name = "processed_messages",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "processed_messages_consumer_name_event_id_key",
                        columnNames = {"consumer_name", "event_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    @Column(name = "consumer_name", nullable = false, length = 100, updatable = false)
    private String consumerName;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 150, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime processedAt;
}
