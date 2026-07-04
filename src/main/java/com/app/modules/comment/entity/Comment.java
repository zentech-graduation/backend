package com.app.modules.comment.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A comment on a post, modelled as an adjacency list with a {@code root_id} ancestor pointer.
 *
 * <p>Maps to the {@code comments} table. The denormalized counters ({@code like_count}, {@code
 * reply_count}) are maintained by Postgres triggers (V16) and are never written from application
 * code. {@code content} is the only mutable column. Soft delete sets {@code deleted_at} — never
 * hard-delete; {@code @SQLRestriction} excludes deleted rows from every query.
 */
@Entity
@Table(name = "comments")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "root_id", updatable = false)
    private UUID rootId;

    @Column(name = "depth", nullable = false, updatable = false)
    private short depth;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "moderation_status", nullable = false)
    private String moderationStatus;

    /** Maintained exclusively by Postgres trigger {@code trg_comment_like_count} (V16). */
    @Column(name = "like_count", insertable = false, updatable = false)
    private int likeCount;

    /** Maintained exclusively by Postgres trigger {@code trg_comment_reply_count} (V16). */
    @Column(name = "reply_count", insertable = false, updatable = false)
    private int replyCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Set by application code on soft delete; {@code null} for live rows (GLOBAL_RULES §6). */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
