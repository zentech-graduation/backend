package com.app.modules.comment.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.comment.dto.response.CommentBroadcastResponse;
import com.app.modules.comment.dto.response.CommentResponse;
import com.app.modules.comment.entity.Comment;
import com.app.modules.comment.mapper.CommentMapper;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.comment.service.CommentCacheService;
import com.app.modules.users.service.UserSummaryService;

import tools.jackson.databind.ObjectMapper;

@Service
public class CommentCacheServiceImpl implements CommentCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommentCacheServiceImpl.class);
    private static final int MAX_CACHED = 50;
    private static final Duration TTL = Duration.ofSeconds(300);
    // v4: the cached shape is CommentBroadcastResponse, which has no isLiked field at all, rather
    // than a CommentResponse with isLiked hardcoded false. The version segment stops a pre-upgrade
    // entry in either older shape from ever being read back as the new type.
    private static final String KEY_PREFIX = "comment:recent:v4:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CommentRepository commentRepository;
    private final CommentMapper mapper;
    private final UserSummaryService userSummaryService;

    public CommentCacheServiceImpl(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CommentRepository commentRepository,
            CommentMapper mapper,
            UserSummaryService userSummaryService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.commentRepository = commentRepository;
        this.mapper = mapper;
        this.userSummaryService = userSummaryService;
    }

    @Override
    public List<CommentBroadcastResponse> getRecent(UUID postId) {
        try {
            List<String> raw = redisTemplate.opsForList().range(key(postId), 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<CommentBroadcastResponse> result = new ArrayList<>(raw.size());
            for (String json : raw) {
                result.add(objectMapper.readValue(json, CommentBroadcastResponse.class));
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Comment cache read failed for post {}: {}", postId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<CommentBroadcastResponse> getOrRebuild(UUID postId) {
        List<CommentBroadcastResponse> cached = getRecent(postId);
        return cached.isEmpty() ? rebuild(postId) : cached;
    }

    @Override
    public void pushToFront(UUID postId, CommentResponse comment) {
        try {
            String key = key(postId);
            CommentBroadcastResponse broadcast = mapper.toBroadcastResponse(comment);
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(broadcast));
            redisTemplate.opsForList().trim(key, 0, MAX_CACHED - 1);
            redisTemplate.expire(key, TTL);
        } catch (RuntimeException e) {
            log.warn("Comment cache push failed for post {}: {}", postId, e.getMessage());
        }
    }

    @Override
    public void invalidate(UUID postId) {
        try {
            redisTemplate.delete(key(postId));
        } catch (RuntimeException e) {
            log.warn("Comment cache invalidate failed for post {}: {}", postId, e.getMessage());
        }
    }

    private List<CommentBroadcastResponse> rebuild(UUID postId) {
        List<Comment> recent =
                commentRepository.findFirstTopLevel(postId, PageRequest.of(0, MAX_CACHED));
        Map<UUID, UserSummaryResponse> authors =
                userSummaryService.loadSummaries(recent.stream().map(Comment::getUserId).toList());
        List<CommentBroadcastResponse> responses =
                recent.stream()
                        .map(
                                c ->
                                        mapper.toBroadcastResponse(
                                                mapper.toResponse(
                                                        c, authors.get(c.getUserId()), false)))
                        .toList();
        try {
            String key = key(postId);
            // Query is newest-first; right-pushing in order keeps index 0 as the newest entry.
            for (CommentBroadcastResponse response : responses) {
                redisTemplate
                        .opsForList()
                        .rightPush(key, objectMapper.writeValueAsString(response));
            }
            if (!responses.isEmpty()) {
                redisTemplate.expire(key, TTL);
            }
        } catch (RuntimeException e) {
            log.warn("Comment cache rebuild failed for post {}: {}", postId, e.getMessage());
        }
        return responses;
    }

    private static String key(UUID postId) {
        return KEY_PREFIX + postId;
    }
}
