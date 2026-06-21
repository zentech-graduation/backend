package com.app.modules.post.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.app.modules.media.entity.MediaAsset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ordered media item attached to a post.
 *
 * <p>Maps to {@code post_media}. {@code media_asset_id} is a plain column reference to the media
 * module's {@link MediaAsset} row; no cross-module JPA association is mapped. Carousel ordering is
 * enforced by {@code UNIQUE (post_id, position)} and the {@code @OrderBy} on {@code Post#media}.
 */
@Entity
@Table(name = "post_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "media_asset_id", nullable = false)
    private UUID mediaAssetId;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(name = "alt_text", columnDefinition = "TEXT")
    private String altText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
