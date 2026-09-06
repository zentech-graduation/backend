package com.app.modules.story.messaging;

/** Versioned story domain event types published through the transactional outbox. */
public final class StoryEventTypes {

    public static final String STORY_VIEWED_V1 = "story.viewed.v1";

    private StoryEventTypes() {}
}
