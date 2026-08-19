package com.app.modules.hashtag.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle of a hashtag in the canonical registry. */
public enum HashtagStatus {

    /** Discoverable everywhere, and the only state a hashtag reaches through ordinary first use. */
    ACTIVE,

    /**
     * Hidden from every hashtag surface, and refused on every post write path.
     *
     * <p>Posts already carrying the tag are untouched and keep appearing wherever they appeared
     * before. Banning hides the tag, never the posts.
     */
    BANNED,

    /**
     * Hidden from every hashtag surface and from the hashtag list on a post.
     *
     * <p>Never a row removal. Deleting the row would cascade to {@code post_hashtags} and drive the
     * {@code post_count} trigger over every post that used the tag.
     */
    DELETED;

    @JsonCreator
    public static HashtagStatus fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : HashtagStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
