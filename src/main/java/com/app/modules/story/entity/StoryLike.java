package com.app.modules.story.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Like relationship between a user and a story.
 *
 * <p>Maps to {@code story_likes}. The compound primary key prevents duplicate likes; {@code
 * stories.like_count} is maintained exclusively by Postgres trigger {@code trg_story_like_count}
 * (V49) — application code must never write the counter.
 */
@Entity
@Table(name = "story_likes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryLike {

    @EmbeddedId private StoryLikeId id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
