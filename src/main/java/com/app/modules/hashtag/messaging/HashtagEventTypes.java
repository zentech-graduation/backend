package com.app.modules.hashtag.messaging;

/** Versioned hashtag index-sync event types published through the transactional outbox. */
public final class HashtagEventTypes {

    public static final String HASHTAG_INDEX_UPSERT_V1 = "hashtag.index.upsert.v1";
    public static final String HASHTAG_INDEX_DELETE_V1 = "hashtag.index.delete.v1";

    private HashtagEventTypes() {}
}
