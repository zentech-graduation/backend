package com.app.modules.story.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link StoryLike} {@code (user_id, story_id)}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StoryLikeId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "story_id")
    private UUID storyId;
}
