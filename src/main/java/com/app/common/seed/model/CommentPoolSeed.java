package com.app.common.seed.model;

import java.util.List;
import java.util.Map;

/**
 * Parsed content of {@code comment_pools.json} in full: the per-topic pool of standalone comment
 * texts, keyed by the same topic tag vocabulary used in {@code posts.json}'s {@code topic_tags},
 * plus the pre-authored multi-turn comment chains ({@code conversation_seeds}) used to seed
 * realistic reply threads.
 */
public record CommentPoolSeed(
        Map<String, List<CommentPoolEntry>> pools, List<CommentChainSeed> conversationSeeds) {}
