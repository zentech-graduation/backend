package com.app.modules.social.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.app.modules.social.converter.FollowStatusConverter;
import com.app.modules.social.enums.FollowStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Follow relationship between two users.
 *
 * <p>The composite key maps to {@code follows(follower_id, following_id)}. Counter updates are
 * handled exclusively by Postgres trigger {@code trg_follow_counts}; application code must not
 * mutate user counters.
 */
@Entity
@Table(name = "follows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {

    @EmbeddedId
    private FollowId id;

    @Convert(converter = FollowStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "follow_status")
    private FollowStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}