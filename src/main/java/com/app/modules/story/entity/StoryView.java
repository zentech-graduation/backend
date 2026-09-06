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
 * Deduplicated view record for a story — one row per (story, viewer) pair.
 *
 * <p>Maps to {@code story_views}. The compound primary key is the dedup guarantee; {@code
 * stories.view_count} is maintained exclusively by Postgres trigger {@code trg_story_view_count}
 * (V16) — application code must never write the counter. Rows are inserted only through the native
 * {@code ON CONFLICT DO NOTHING} query, so {@code viewed_at} relies on its DB default.
 */
@Entity
@Table(name = "story_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryView {

    @EmbeddedId private StoryViewId id;

    @Column(name = "viewed_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime viewedAt;
}
