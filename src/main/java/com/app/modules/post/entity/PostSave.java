package com.app.modules.post.entity;

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
 * Save (bookmark) relationship between a user and a post.
 *
 * <p>Maps to {@code post_saves}. The compound primary key prevents duplicate saves; {@code
 * posts.save_count} is maintained exclusively by Postgres trigger {@code trg_post_save_count} (V16)
 * — application code must never write the counter.
 */
@Entity
@Table(name = "post_saves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostSave {

    @EmbeddedId private PostSaveId id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
