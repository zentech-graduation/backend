package com.app.modules.hashtag.entity;

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
 * Periodic trending snapshot for a hashtag.
 *
 * <p>Maps to the {@code hashtag_trending} table. Rows are written exclusively by the {@code
 * HashtagTrendingServiceImpl} scheduled job; direct writes from the request path are forbidden.
 */
@Entity
@Table(name = "hashtag_trending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HashtagTrending {

    @EmbeddedId private HashtagTrendingId id;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "post_count", nullable = false)
    private int postCount;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
