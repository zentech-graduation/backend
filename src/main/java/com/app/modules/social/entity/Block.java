package com.app.modules.social.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.*;

/**
 * A directional block from one user to another.
 *
 * <p>A single row suppresses interaction and content visibility in both directions between the
 * pair, even though the row itself records only blocker to blocked. Creating a block deletes any
 * follow relationship between the pair in either direction.
 */
@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    @EmbeddedId private BlockId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
