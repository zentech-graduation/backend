package com.app.modules.post.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link PostUserTag} {@code (post_id, tagged_user_id)}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostUserTagId implements Serializable {

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "tagged_user_id")
    private UUID taggedUserId;
}
