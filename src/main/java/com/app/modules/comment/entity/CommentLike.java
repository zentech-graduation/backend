package com.app.modules.comment.entity;

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
 * Like relationship between a user and a comment.
 *
 * <p>Maps to {@code comment_likes}. The compound primary key prevents duplicate likes; {@code
 * comments.like_count} is maintained exclusively by Postgres trigger {@code trg_comment_like_count}
 * (V16) — application code must never write the counter.
 */
@Entity
@Table(name = "comment_likes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentLike {

    @EmbeddedId private CommentLikeId id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
