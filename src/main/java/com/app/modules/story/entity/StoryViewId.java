package com.app.modules.story.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link StoryView} {@code (story_id, viewer_id)}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StoryViewId implements Serializable {

    @Column(name = "story_id")
    private UUID storyId;

    @Column(name = "viewer_id")
    private UUID viewerId;
}
