package com.app.modules.hashtag.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canonical hashtag registry.
 *
 * <p>Maps to the {@code hashtags} table. The {@code name} is stored without the {@code #} prefix
 * and lowercased; normalization is enforced by {@code HashtagServiceImpl}. The {@code postCount}
 * counter is maintained exclusively by the Postgres trigger {@code trg_hashtag_post_count} (V16)
 * and is intentionally marked {@code insertable = false, updatable = false}.
 */
@Entity
@Table(name = "hashtags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /** Maintained exclusively by Postgres trigger {@code trg_hashtag_post_count} (V16). */
    @Column(name = "post_count", insertable = false, updatable = false)
    private int postCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
