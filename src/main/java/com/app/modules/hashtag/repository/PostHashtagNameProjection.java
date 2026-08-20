package com.app.modules.hashtag.repository;

import java.util.UUID;

/** One post-to-hashtag association carrying the hashtag's name, for post response hydration. */
public interface PostHashtagNameProjection {

    UUID getPostId();

    UUID getHashtagId();

    String getName();
}
