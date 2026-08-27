package com.app.common.seed.model;

import java.util.List;

public record CommentChainSeed(String id, String topicTag, List<CommentChainTurn> turns) {}
