package com.app.modules.recommendation.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite identifier for {@link UserEvent}.
 *
 * <p>{@code user_events} is partitioned by {@code created_at}, so PostgreSQL requires the partition
 * key to be part of every unique constraint including the primary key. The identifier is therefore
 * {@code (id, created_at)} rather than {@code id} alone.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserEventId implements Serializable {

    private UUID id;

    private OffsetDateTime createdAt;
}
