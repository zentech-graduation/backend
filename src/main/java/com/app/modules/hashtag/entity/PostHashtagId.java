package com.app.modules.hashtag.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link PostHashtag} {@code (post_id, hashtag_id)}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostHashtagId implements Serializable {

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "hashtag_id")
    private UUID hashtagId;
}
