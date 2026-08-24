package com.app.common.seed.model;

import java.util.List;

public record PersonaSeed(
        String id,
        String label,
        String occupation,
        String ageBand,
        String city,
        String language,
        String voice,
        List<String> topics,
        String postingFrequency,
        String mediaPreference,
        String engagementStyle,
        String accountType) {}
