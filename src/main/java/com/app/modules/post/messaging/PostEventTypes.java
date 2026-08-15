package com.app.modules.post.messaging;

/**
 * Versioned post domain event types published through the transactional outbox.
 *
 * <p>Post routing keys are namespaced by the tier that consumes them, not by the domain verb alone.
 * {@code post.index.*} feeds the Elasticsearch sync queue; {@code post.live.*} feeds the realtime
 * fanout tier. The two namespaces are structurally disjoint so the live tier's exchange-to-exchange
 * binding cannot match an index-sync event, which a {@code post.#} binding would have done.
 */
public final class PostEventTypes {

    public static final String POST_INDEX_UPSERT_V1 = "post.index.upsert.v1";
    public static final String POST_INDEX_DELETE_V1 = "post.index.delete.v1";
    public static final String POST_LIKED_V1 = "post.liked.v1";
    public static final String POST_SAVED_V1 = "post.saved.v1";
    public static final String POST_VIEWED_V1 = "post.viewed.v1";

    public static final String POST_LIVE_LIKED_V1 = "post.live.liked.v1";
    public static final String POST_LIVE_UNLIKED_V1 = "post.live.unliked.v1";

    private PostEventTypes() {}
}
