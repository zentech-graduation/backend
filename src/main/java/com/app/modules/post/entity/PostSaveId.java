package com.app.modules.post.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link PostSave} {@code (user_id, post_id)}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostSaveId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "post_id")
    private UUID postId;
}
