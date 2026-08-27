package com.app.common.seed.model;

import java.util.List;

public record MediaManifestEntry(
        String id,
        String kind,
        String role,
        List<String> topicTags,
        long pexelsId,
        String pexelsUrl,
        String photographer,
        String license,
        int width,
        int height,
        Integer durationSeconds,
        String mimeType,
        long fileSizeBytes,
        String storageKey,
        String cdnUrl) {}
