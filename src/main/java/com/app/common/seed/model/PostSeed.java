package com.app.common.seed.model;

import java.util.List;

public record PostSeed(
        String id,
        String authorUsername,
        String caption,
        String postType,
        List<String> mediaRefs,
        List<String> hashtagNames,
        int createdAtOffsetMinutes,
        String status,
        String engagementBand,
        List<String> topicTags,
        boolean isModerationCase,
        String narrativeId,
        String timeHint) {}
