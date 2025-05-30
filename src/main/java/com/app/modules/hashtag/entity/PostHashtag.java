package com.app.modules.hashtag.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Junction table associating a post to a hashtag.
 *
 * <p>Maps to the {@code post_hashtags} table. Deletion fires the Postgres trigger {@code
 * trg_hashtag_post_count} (V16), which decrements {@code hashtags.post_count} via {@code
 * GREATEST(post_count - 1, 0)}.
 */
@Entity
@Table(name = "post_hashtags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostHashtag {

    @EmbeddedId private PostHashtagId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
