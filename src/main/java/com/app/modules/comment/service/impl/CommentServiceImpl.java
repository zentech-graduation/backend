package com.app.modules.comment.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.comment.config.CommentProperties;
import com.app.modules.comment.dto.request.CreateCommentRequest;
import com.app.modules.comment.dto.request.EditCommentRequest;
import com.app.modules.comment.dto.response.CommentResponse;
import com.app.modules.comment.entity.Comment;
import com.app.modules.comment.entity.CommentLike;
import com.app.modules.comment.entity.CommentLikeId;
import com.app.modules.comment.entity.CommentWriteIdempotency;
import com.app.modules.comment.mapper.CommentMapper;
import com.app.modules.comment.messaging.CommentEventTypes;
import com.app.modules.comment.observability.CommentMetrics;
import com.app.modules.comment.repository.CommentIdempotencyRepository;
import com.app.modules.comment.repository.CommentLikeRepository;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.comment.repository.CommentUserRepository;
import com.app.modules.comment.service.CommentAccessPolicyService;
import com.app.modules.comment.service.CommentCacheService;
import com.app.modules.comment.service.CommentModerationService;
import com.app.modules.comment.service.CommentService;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostVisibilityService;

import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Pattern MENTION = Pattern.compile("@([a-zA-Z0-9_]{1,30})");
    private static final int MAX_MENTIONS = 10;
    private static final int MAX_DEPTH = 10;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String AGGREGATE_TYPE = "comment";

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentIdempotencyRepository idempotencyRepository;
    private final PostRepository postRepository;
    private final CommentUserRepository commentUserRepository;
    private final CommentModerationService moderationService;
    private final CommentAccessPolicyService accessPolicy;
    private final CommentMapper mapper;
    private final OutboxService outboxService;
    private final CommentProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CommentCacheService cacheService;
    private final CommentMetrics metrics;
    private final PostVisibilityService postVisibilityService;

    public CommentServiceImpl(
            CommentRepository commentRepository,
            CommentLikeRepository commentLikeRepository,
            CommentIdempotencyRepository idempotencyRepository,
            PostRepository postRepository,
            CommentUserRepository commentUserRepository,
            CommentModerationService moderationService,
            CommentAccessPolicyService accessPolicy,
            CommentMapper mapper,
            OutboxService outboxService,
            CommentProperties properties,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CommentCacheService cacheService,
            CommentMetrics metrics,
            PostVisibilityService postVisibilityService) {
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.postRepository = postRepository;
        this.commentUserRepository = commentUserRepository;
        this.moderationService = moderationService;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
        this.metrics = metrics;
        this.postVisibilityService = postVisibilityService;
    }

    @Override
    @Transactional
    public CommentResponse createComment(
            UUID actorId, CreateCommentRequest request, String idempotencyKey) {
        Timer.Sample sample = Timer.start();
        MDC.put("postId", String.valueOf(request.postId()));
        MDC.put("userId", String.valueOf(actorId));
        try {
            return doCreateComment(actorId, request, idempotencyKey);
        } finally {
            sample.stop(metrics.createLatency());
            clearWriteMdc();
        }
    }

    private CommentResponse doCreateComment(
            UUID actorId, CreateCommentRequest request, String idempotencyKey) {
        Post post =
                postRepository
                        .findById(request.postId())
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        accessPolicy.assertCanComment(actorId, post);

        short depth = 0;
        UUID rootId = null;
        UUID parentOwnerId = null;
        if (request.parentId() != null) {
            Comment parent =
                    commentRepository
                            .findByIdAndDeletedAtIsNull(request.parentId())
                            .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
            depth = (short) (parent.getDepth() + 1);
            if (depth > MAX_DEPTH) {
                throw new AppException(ApiErrorCode.COMMENT_DEPTH_EXCEEDED);
            }
            rootId = parent.getRootId() != null ? parent.getRootId() : parent.getId();
            parentOwnerId = parent.getUserId();
        }

        String content =
                com.app.modules.comment.util.CommentContentNormalizer.normalize(request.content());
        CommentModerationService.ModerationResult moderation = moderationService.check(content);
        if (moderation.rejected()) {
            metrics.moderationRejected(moderation.reason());
            throw new AppException(ApiErrorCode.COMMENT_MODERATION_REJECTED);
        }

        // Reserve the idempotency key in the same transaction via ON CONFLICT DO NOTHING. A
        // duplicate key returns a clean replay/conflict instead of poisoning the transaction with a
        // constraint violation, and a rolled-back create frees the key.
        String requestHash = sha256(post.getId() + "|" + request.parentId() + "|" + content);
        if (idempotencyKey != null
                && idempotencyRepository.insertIfAbsent(actorId, idempotencyKey, requestHash)
                        == 0) {
            return replayOrConflict(actorId, idempotencyKey, requestHash);
        }

        enforceSlowMode(post.getId(), actorId);

        Comment saved =
                commentRepository.save(
                        Comment.builder()
                                .postId(post.getId())
                                .userId(actorId)
                                .parentId(request.parentId())
                                .rootId(rootId)
                                .depth(depth)
                                .content(content)
                                .moderationStatus("approved")
                                .build());
        MDC.put("commentId", saved.getId().toString());

        CommentResponse response = mapper.toResponse(saved);

        if (idempotencyKey != null) {
            idempotencyRepository.updateResponseBody(
                    actorId, idempotencyKey, objectMapper.writeValueAsString(response));
        }

        List<String> mentionedUserIds = resolveMentions(content, actorId);
        enqueueCreated(post, saved, actorId, parentOwnerId, mentionedUserIds, response);
        CommentResponse cached = response;
        afterCommit(() -> cacheService.pushToFront(post.getId(), cached));
        return response;
    }

    @Override
    @Transactional
    public CommentResponse editComment(UUID actorId, UUID commentId, EditCommentRequest request) {
        putWriteMdc(commentId, actorId);
        try {
            Comment comment =
                    commentRepository
                            .findByIdAndDeletedAtIsNull(commentId)
                            .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
            if (!comment.getUserId().equals(actorId)) {
                throw new AppException(ApiErrorCode.COMMENT_FORBIDDEN);
            }
            MDC.put("postId", comment.getPostId().toString());
            String content =
                    com.app.modules.comment.util.CommentContentNormalizer.normalize(
                            request.content());
            CommentModerationService.ModerationResult moderation = moderationService.check(content);
            if (moderation.rejected()) {
                metrics.moderationRejected(moderation.reason());
                throw new AppException(ApiErrorCode.COMMENT_MODERATION_REJECTED);
            }
            comment.setContent(content);
            Comment saved = commentRepository.save(comment);
            CommentResponse response = mapper.toResponse(saved);

            Map<String, Object> data = new HashMap<>();
            data.put("postId", saved.getPostId().toString());
            data.put("commentId", saved.getId().toString());
            data.put("depth", (int) saved.getDepth());
            data.put("comment", response);
            outboxService.enqueue(
                    CommentEventTypes.COMMENT_EDITED_V1,
                    CommentEventTypes.COMMENT_EDITED_V1,
                    AGGREGATE_TYPE,
                    saved.getId(),
                    actorId,
                    data);
            afterCommit(() -> cacheService.invalidate(saved.getPostId()));
            return response;
        } finally {
            clearWriteMdc();
        }
    }

    @Override
    @Transactional
    public void deleteComment(UUID actorId, UUID commentId) {
        putWriteMdc(commentId, actorId);
        try {
            Comment comment =
                    commentRepository
                            .findByIdAndDeletedAtIsNull(commentId)
                            .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
            if (!comment.getUserId().equals(actorId) && !isCurrentUserAdmin()) {
                throw new AppException(ApiErrorCode.COMMENT_FORBIDDEN);
            }
            MDC.put("postId", comment.getPostId().toString());
            commentRepository.softDeleteSubtree(commentId, OffsetDateTime.now());

            Map<String, Object> data = new HashMap<>();
            data.put("postId", comment.getPostId().toString());
            data.put("commentId", comment.getId().toString());
            if (comment.getRootId() != null) {
                data.put("rootId", comment.getRootId().toString());
            }
            outboxService.enqueue(
                    CommentEventTypes.COMMENT_DELETED_V1,
                    CommentEventTypes.COMMENT_DELETED_V1,
                    AGGREGATE_TYPE,
                    comment.getId(),
                    actorId,
                    data);
            afterCommit(() -> cacheService.invalidate(comment.getPostId()));
        } finally {
            clearWriteMdc();
        }
    }

    @Override
    @Transactional
    public void likeComment(UUID actorId, UUID commentId) {
        putWriteMdc(commentId, actorId);
        try {
            Comment comment =
                    commentRepository
                            .findByIdAndDeletedAtIsNull(commentId)
                            .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
            if (comment.getUserId().equals(actorId)) {
                throw new AppException(ApiErrorCode.COMMENT_FORBIDDEN);
            }
            MDC.put("postId", comment.getPostId().toString());
            if (commentLikeRepository.existsByIdUserIdAndIdCommentId(actorId, commentId)) {
                throw new AppException(ApiErrorCode.COMMENT_ALREADY_LIKED);
            }
            commentLikeRepository.save(
                    CommentLike.builder().id(new CommentLikeId(actorId, commentId)).build());

            Map<String, Object> data = new HashMap<>();
            data.put("postId", comment.getPostId().toString());
            data.put("commentId", comment.getId().toString());
            data.put("commentOwnerId", comment.getUserId().toString());
            outboxService.enqueue(
                    CommentEventTypes.COMMENT_LIKED_V1,
                    CommentEventTypes.COMMENT_LIKED_V1,
                    AGGREGATE_TYPE,
                    comment.getId(),
                    actorId,
                    data);
        } finally {
            clearWriteMdc();
        }
    }

    @Override
    @Transactional
    public void unlikeComment(UUID actorId, UUID commentId) {
        putWriteMdc(commentId, actorId);
        try {
            Comment comment =
                    commentRepository
                            .findByIdAndDeletedAtIsNull(commentId)
                            .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
            MDC.put("postId", comment.getPostId().toString());
            int removed = commentLikeRepository.deleteByUserAndComment(actorId, commentId);
            if (removed == 0) {
                throw new AppException(ApiErrorCode.COMMENT_NOT_LIKED);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("postId", comment.getPostId().toString());
            data.put("commentId", comment.getId().toString());
            outboxService.enqueue(
                    CommentEventTypes.COMMENT_UNLIKED_V1,
                    CommentEventTypes.COMMENT_UNLIKED_V1,
                    AGGREGATE_TYPE,
                    comment.getId(),
                    actorId,
                    data);
        } finally {
            clearWriteMdc();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> listTopLevelComments(
            UUID viewerId, UUID postId, String cursor, int limit) {
        Post post =
                postRepository
                        .findById(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        assertCanRead(viewerId, post);
        int pageSize = normalizeLimit(limit);
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<Comment> comments =
                cursorTime == null
                        ? commentRepository.findFirstTopLevel(postId, page)
                        : commentRepository.findTopLevelBefore(postId, cursorTime, page);
        return toPage(comments, pageSize, cursor);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> listReplies(
            UUID viewerId, UUID commentId, String cursor, int limit) {
        Comment parent =
                commentRepository
                        .findByIdAndDeletedAtIsNull(commentId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.COMMENT_NOT_FOUND));
        Post post =
                postRepository
                        .findById(parent.getPostId())
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        assertCanRead(viewerId, post);
        int pageSize = normalizeLimit(limit);
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<Comment> replies =
                cursorTime == null
                        ? commentRepository.findFirstReplies(commentId, page)
                        : commentRepository.findRepliesBefore(commentId, cursorTime, page);
        return toPage(replies, pageSize, cursor);
    }

    // Read-side visibility gate, consistent with the create path and the WebSocket SUBSCRIBE
    // interceptor. A null viewer (anonymous) cannot read; post visibility covers block and
    // private-follow rules.
    private void assertCanRead(UUID viewerId, Post post) {
        if (viewerId == null || !postVisibilityService.isVisibleTo(viewerId, post)) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
    }

    // Keyset pagination over limit+1 rows: hasNextPage is decided by the pre-trim size, then the
    // extra probe row is dropped.
    private CursorPageResponse<CommentResponse> toPage(
            List<Comment> rows, int pageSize, String cursor) {
        boolean hasNextPage = rows.size() > pageSize;
        List<Comment> page = hasNextPage ? rows.subList(0, pageSize) : rows;
        List<CommentResponse> content = page.stream().map(mapper::toResponse).toList();
        String startCursor = page.isEmpty() ? null : encodeCursor(page.get(0).getCreatedAt());
        String endCursor =
                page.isEmpty() ? null : encodeCursor(page.get(page.size() - 1).getCreatedAt());
        return CursorPageResponse.<CommentResponse>builder()
                .content(content)
                .pageInfo(
                        CursorPageResponse.PageInfo.builder()
                                .hasNextPage(hasNextPage)
                                .hasPreviousPage(cursor != null)
                                .startCursor(startCursor)
                                .endCursor(endCursor)
                                .build())
                .build();
    }

    // Re-reads the existing idempotency row to replay the cached response or reject a key reuse
    // with
    // a different payload. A null body means a concurrent in-flight create reserved the key but has
    // not committed its response yet, which is treated as a conflict.
    private CommentResponse replayOrConflict(
            UUID actorId, String idempotencyKey, String requestHash) {
        CommentWriteIdempotency row =
                idempotencyRepository
                        .findByUserIdAndIdempotencyKey(actorId, idempotencyKey)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.COMMENT_IDEMPOTENCY_CONFLICT));
        if (!requestHash.equals(row.getRequestHash()) || row.getResponseBody() == null) {
            throw new AppException(ApiErrorCode.COMMENT_IDEMPOTENCY_CONFLICT);
        }
        metrics.idempotencyReplay();
        try {
            return objectMapper.readValue(row.getResponseBody(), CommentResponse.class);
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.INTERNAL_ERROR, "Failed to read cached response");
        }
    }

    // Pre-persist SETNX: a rolled-back create can briefly hold the slow-mode key, which is
    // acceptable at the small TTLs slow mode uses.
    private void enforceSlowMode(UUID postId, UUID actorId) {
        int seconds = properties.slowModeSeconds();
        if (seconds <= 0) {
            return;
        }
        String key = "comment:slowmode:" + postId + ":" + actorId;
        Boolean acquired =
                redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(seconds));
        if (Boolean.FALSE.equals(acquired)) {
            throw new AppException(ApiErrorCode.COMMENT_SLOW_MODE_ACTIVE);
        }
    }

    private void enqueueCreated(
            Post post,
            Comment saved,
            UUID actorId,
            UUID parentOwnerId,
            List<String> mentionedUserIds,
            CommentResponse response) {
        // The outbox stores data via Map.copyOf, which rejects null values, so null-valued keys are
        // omitted rather than inserted.
        Map<String, Object> data = new HashMap<>();
        data.put("postId", post.getId().toString());
        data.put("postOwnerId", post.getUserId().toString());
        data.put("commentId", saved.getId().toString());
        data.put("userId", actorId.toString());
        data.put("depth", (int) saved.getDepth());
        data.put("mentionedUserIds", mentionedUserIds);
        data.put("comment", response);
        if (saved.getParentId() != null) {
            data.put("parentId", saved.getParentId().toString());
        }
        if (parentOwnerId != null) {
            data.put("parentOwnerId", parentOwnerId.toString());
        }
        if (saved.getRootId() != null) {
            data.put("rootId", saved.getRootId().toString());
        }
        outboxService.enqueue(
                CommentEventTypes.COMMENT_CREATED_V1,
                CommentEventTypes.COMMENT_CREATED_V1,
                AGGREGATE_TYPE,
                saved.getId(),
                actorId,
                data);
    }

    private List<String> resolveMentions(String content, UUID actorId) {
        Set<String> usernames = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find() && usernames.size() < MAX_MENTIONS) {
            usernames.add(matcher.group(1));
        }
        List<String> ids = new java.util.ArrayList<>();
        for (String username : usernames) {
            commentUserRepository
                    .findByUsernameAndDeletedAtIsNull(username)
                    .map(u -> u.getId())
                    .filter(id -> !id.equals(actorId))
                    .ifPresent(id -> ids.add(id.toString()));
        }
        return ids;
    }

    // Cache mutations run only after the write commits so a rolled-back transaction never leaks
    // into the cache; outside a transaction the action runs inline.
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    // MDC keys are not in the current logback console pattern; they exist for correlation in log
    // aggregation. commentId is added by the caller once the row is persisted.
    private static void putWriteMdc(UUID commentId, UUID actorId) {
        MDC.put("commentId", String.valueOf(commentId));
        MDC.put("userId", String.valueOf(actorId));
    }

    private static void clearWriteMdc() {
        MDC.remove("commentId");
        MDC.remove("postId");
        MDC.remove("userId");
    }

    private boolean isCurrentUserAdmin() {
        try {
            return SecurityUtils.isAdmin();
        } catch (AppException e) {
            return false;
        }
    }

    private int normalizeLimit(int limit) {
        return limit > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : (limit < 1 ? DEFAULT_PAGE_SIZE : limit);
    }

    private String encodeCursor(OffsetDateTime time) {
        if (time == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(time.toString().getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(
                    new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
