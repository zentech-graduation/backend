package com.app.common.seed.model;

import java.util.List;
import java.util.Map;

public record ConversationSeed(
        String id,
        List<String> participants,
        String scenario,
        List<MessageSeed> messages,
        List<String> unreadFor,
        List<String> manuallyUnreadFor,
        Map<String, String> nicknames) {}
