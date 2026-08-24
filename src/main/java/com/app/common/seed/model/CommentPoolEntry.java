package com.app.common.seed.model;

public record CommentPoolEntry(
        String text, String tone, String language, String depthSuitability, boolean isReplyBait) {}
