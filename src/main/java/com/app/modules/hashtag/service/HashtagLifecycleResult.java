package com.app.modules.hashtag.service;

import java.util.UUID;

import com.app.modules.hashtag.dto.response.HashtagAdminResponse;
import com.app.modules.hashtag.enums.HashtagStatus;

/**
 * Outcome of one hashtag lifecycle transition.
 *
 * <p>Carries what the audit row needs and nothing a caller could have supplied: the state the
 * hashtag left, the state it now holds, and how many trending rows the transition purged. All three
 * are facts this transaction established.
 *
 * @param hashtagId the hashtag the transition applied to
 * @param name the hashtag's normalized name
 * @param previousStatus the state the hashtag held before, or null when it was just created
 * @param status the state it holds now
 * @param purgedTrendingRows trending snapshot rows removed by this transition
 * @param hashtag the hashtag as the administrative surface renders it
 */
public record HashtagLifecycleResult(
        UUID hashtagId,
        String name,
        HashtagStatus previousStatus,
        HashtagStatus status,
        int purgedTrendingRows,
        HashtagAdminResponse hashtag) {}
