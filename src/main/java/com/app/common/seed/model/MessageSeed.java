package com.app.common.seed.model;

public record MessageSeed(
        String sender,
        String text,
        String messageType,
        int offsetMinutesFromConversationStart,
        Integer adminRemovedAtOffsetMinutes,
        boolean isDeleted) {}
