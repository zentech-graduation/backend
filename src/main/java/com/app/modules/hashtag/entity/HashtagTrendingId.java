package com.app.modules.hashtag.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link HashtagTrending} {@code (hashtag_id, period_start)}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HashtagTrendingId implements Serializable {

    @Column(name = "hashtag_id")
    private UUID hashtagId;

    @Column(name = "period_start")
    private OffsetDateTime periodStart;
}
