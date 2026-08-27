package com.app.common.seed.model;

import java.util.List;

public record ConversationSeed(
        String id,
        List<String> participants,
        String scenario,
        List<MessageSeed> messages,
        List<String> unreadFor) {}
