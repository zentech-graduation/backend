package com.app.common.seed.model;

import java.util.List;

public record HashtagSeed(
        String name,
        String status,
        List<String> topicTags,
        boolean isTrending,
        String intendedPostCountBand) {}
