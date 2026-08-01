package com.app.modules.post.messaging;

/** Versioned post event types published through the transactional outbox. */
public final class PostEventTypes {

    public static final String POST_INDEX_UPSERT_V1 = "post.index.upsert.v1";
    public static final String POST_INDEX_DELETE_V1 = "post.index.delete.v1";
    public static final String POST_LIKED_V1 = "post.liked.v1";
    public static final String POST_SAVED_V1 = "post.saved.v1";

    private PostEventTypes() {}
}
