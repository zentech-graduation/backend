package com.app.modules.post.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import com.app.modules.post.converter.PostStatusConverter;
import com.app.modules.post.converter.PostTypeConverter;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aggregate root for a user post.
 *
 * <p>Maps to the {@code posts} table. The denormalized counters ({@code like_count}, {@code
 * comment_count}, {@code save_count}) are maintained by Postgres triggers (V16) and {@code
 * view_count} by a background job only; none are written from application code. Soft delete sets
 * {@code deleted_at} and {@code status = removed} — never hard-delete.
 */
@Entity
@Table(name = "posts")
// Soft-delete filter: every query on posts must exclude deleted rows (GLOBAL_RULES §3).
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Convert(converter = PostTypeConverter.class)
    @Column(name = "post_type", nullable = false, columnDefinition = "post_type")
    private PostType postType;

    @Convert(converter = PostStatusConverter.class)
    @Column(name = "status", nullable = false, columnDefinition = "post_status")
    private PostStatus status;

    /** Maintained exclusively by Postgres trigger {@code trg_post_like_count} (V16). */
    @Column(name = "like_count", insertable = false, updatable = false)
    private int likeCount;

    /** Maintained exclusively by Postgres trigger {@code trg_post_comment_count} (V16). */
    @Column(name = "comment_count", insertable = false, updatable = false)
    private int commentCount;

    /** Maintained exclusively by Postgres trigger {@code trg_post_save_count} (V16). */
    @Column(name = "save_count", insertable = false, updatable = false)
    private int saveCount;

    /** Updated by a background job only — never written from request paths (GLOBAL_RULES §2). */
    @Column(name = "view_count", insertable = false, updatable = false)
    private int viewCount;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Set by application code on soft delete; {@code null} for live rows (GLOBAL_RULES §6). */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    // Batches the lazy media-collection initialization across a page of posts into one IN query,
    // so a list endpoint issues a single media load instead of one per post.
    @BatchSize(size = 100)
    @Builder.Default
    private List<PostMedia> media = new ArrayList<>();
}
