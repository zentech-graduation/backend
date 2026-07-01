package com.app.modules.post.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.outbox.service.OutboxService;
import com.app.common.response.CursorPageResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.common.settings.service.SystemSettingService;
import com.app.modules.hashtag.service.HashtagService;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.enums.MediaType;
import com.app.modules.post.dto.request.CreatePostRequest;
import com.app.modules.post.dto.request.PostStatusTransitionRequest;
import com.app.modules.post.dto.request.UpdatePostCaptionRequest;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.dto.response.PostEditHistoryResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostEditHistory;
import com.app.modules.post.entity.PostMedia;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.enums.PostType;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.messaging.PostEventTypes;
import com.app.modules.post.repository.PostEditHistoryRepository;
import com.app.modules.post.repository.PostMediaAssetRepository;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.repository.PostUserRepository;
import com.app.modules.post.service.PostService;
import com.app.modules.post.service.PostVisibilityService;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.entity.User;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_]+)");
    private static final String MAX_POST_MEDIA_ITEMS = "max_post_media_items";
    private static final String MAX_HASHTAGS_PER_POST = "max_hashtags_per_post";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostEditHistoryRepository postEditHistoryRepository;
    private final PostUserRepository postUserRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;
    private final HashtagService hashtagService;
    private final SystemSettingService systemSettingService;
    private final PostVisibilityService postVisibilityService;
    private final PostResponseAssembler postResponseAssembler;
    private final PostMapper postMapper;
    private final SocialService socialService;
    private final OutboxService outboxService;

    public PostServiceImpl(
            PostRepository postRepository,
            PostEditHistoryRepository postEditHistoryRepository,
            PostUserRepository postUserRepository,
            PostMediaAssetRepository postMediaAssetRepository,
            HashtagService hashtagService,
            SystemSettingService systemSettingService,
            PostVisibilityService postVisibilityService,
            PostResponseAssembler postResponseAssembler,
            PostMapper postMapper,
            SocialService socialService,
            OutboxService outboxService) {
        this.postRepository = postRepository;
        this.postEditHistoryRepository = postEditHistoryRepository;
        this.postUserRepository = postUserRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.hashtagService = hashtagService;
        this.systemSettingService = systemSettingService;
        this.postVisibilityService = postVisibilityService;
        this.postResponseAssembler = postResponseAssembler;
        this.postMapper = postMapper;
        this.socialService = socialService;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public PostResponse createPost(UUID authorId, CreatePostRequest request) {
        PostStatus initialStatus =
                request.status() == null ? PostStatus.PUBLISHED : request.status();
        if (initialStatus != PostStatus.DRAFT && initialStatus != PostStatus.PUBLISHED) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid initial post status");
        }
        List<UUID> mediaIds = request.mediaIds();
        if (new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Duplicate media ids");
        }
        Map<UUID, MediaAsset> assets =
                postMediaAssetRepository.findAllById(mediaIds).stream()
                        .collect(Collectors.toMap(MediaAsset::getId, a -> a));
        if (assets.size() != mediaIds.size()) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Media asset not found");
        }
        for (MediaAsset asset : assets.values()) {
            if (!asset.getUserId().equals(authorId)) {
                throw new AppException(
                        ApiErrorCode.POST_FORBIDDEN, "Media asset not owned by author");
            }
        }
        validateMediaCardinality(request.postType(), mediaIds, assets);

        Post post =
                Post.builder()
                        .userId(authorId)
                        .caption(request.caption())
                        .postType(request.postType())
                        .status(initialStatus)
                        .locationName(request.locationName())
                        .latitude(request.latitude())
                        .longitude(request.longitude())
                        .build();
        for (int i = 0; i < mediaIds.size(); i++) {
            post.getMedia()
                    .add(
                            PostMedia.builder()
                                    .post(post)
                                    .mediaAssetId(mediaIds.get(i))
                                    .position((short) i)
                                    .build());
        }
        // Flush so the DB-assigned id and @CreationTimestamp createdAt are populated before the
        // index-upsert event payload reads them.
        postRepository.saveAndFlush(post);
        if (initialStatus == PostStatus.PUBLISHED) {
            upsertCaptionHashtags(post.getId(), post.getCaption());
            enqueuePostIndexUpsert(post);
        }
        log.info("Post created: postId={}, type={}", post.getId(), post.getPostType());
        return postResponseAssembler.assemble(post);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID viewerId, UUID postId) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        boolean isOwner = viewerId.equals(post.getUserId());
        // Non-owners must not learn that an unpublished post exists.
        if (!isOwner && post.getStatus() != PostStatus.PUBLISHED) {
            throw new AppException(ApiErrorCode.POST_NOT_FOUND);
        }
        if (!postVisibilityService.isVisibleTo(viewerId, post)) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        return postResponseAssembler.assemble(post);
    }

    @Override
    @Transactional
    public PostResponse updateCaption(
            UUID requesterId, UUID postId, UpdatePostCaptionRequest request) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        if (!requesterId.equals(post.getUserId())) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        // The audit row records the pre-edit caption before any mutation.
        postEditHistoryRepository.save(
                PostEditHistory.builder()
                        .postId(post.getId())
                        .editorId(requesterId)
                        .previousCaption(post.getCaption())
                        .build());
        post.setCaption(request.caption());
        if (post.getStatus() == PostStatus.PUBLISHED) {
            // Keep post_hashtags consistent with the published caption.
            hashtagService.removeHashtagsForPost(post.getId());
            upsertCaptionHashtags(post.getId(), post.getCaption());
            enqueuePostIndexUpsert(post);
        }
        return postResponseAssembler.assemble(post);
    }

    @Override
    @Transactional
    public PostResponse transitionStatus(
            UUID requesterId, UUID postId, PostStatusTransitionRequest request) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        PostStatus target = request.targetStatus();
        boolean isOwner = requesterId.equals(post.getUserId());
        if (target == PostStatus.REMOVED) {
            if (!isOwner && !isCurrentUserAdmin()) {
                throw new AppException(ApiErrorCode.POST_FORBIDDEN);
            }
            softDelete(post);
            return postResponseAssembler.assemble(post);
        }
        if (!isOwner) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        PostStatus current = post.getStatus();
        boolean allowed =
                (current == PostStatus.DRAFT && target == PostStatus.PUBLISHED)
                        || (current == PostStatus.PUBLISHED && target == PostStatus.ARCHIVED)
                        || (current == PostStatus.ARCHIVED && target == PostStatus.PUBLISHED);
        if (!allowed) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid status transition");
        }
        post.setStatus(target);
        if (target == PostStatus.PUBLISHED) {
            upsertCaptionHashtags(post.getId(), post.getCaption());
            enqueuePostIndexUpsert(post);
        } else if (target == PostStatus.ARCHIVED) {
            // Unpublishing removes hashtag associations (hashtag module data rules).
            hashtagService.removeHashtagsForPost(post.getId());
            enqueuePostIndexDelete(post);
        }
        log.info(
                "Post status transitioned: postId={}, from={}, to={}",
                post.getId(),
                current,
                target);
        return postResponseAssembler.assemble(post);
    }

    @Override
    @Transactional
    public void deletePost(UUID requesterId, UUID postId) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        if (!requesterId.equals(post.getUserId()) && !isCurrentUserAdmin()) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        softDelete(post);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> listUserPosts(
            UUID viewerId, UUID targetUserId, String cursor, int size) {
        User target =
                postUserRepository
                        .findByIdAndDeletedAtIsNull(targetUserId)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.NOT_FOUND, "User not found"));
        if (socialService.isBlockedBetween(viewerId, targetUserId)) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
        }
        boolean isOwner = viewerId.equals(targetUserId);
        if (target.isPrivate()
                && !isOwner
                && !socialService.hasAcceptedFollow(viewerId, targetUserId)) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        int pageSize = normalizeLimit(size);
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<Post> posts =
                cursorTime == null
                        ? postRepository.findFirstUserPosts(
                                targetUserId, PostStatus.PUBLISHED, page)
                        : postRepository.findUserPostsBefore(
                                targetUserId, PostStatus.PUBLISHED, cursorTime, page);
        if (posts.size() > pageSize) {
            posts = posts.subList(0, pageSize);
        }
        if (posts.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
        }
        List<PostResponse> content = postResponseAssembler.assemble(posts);
        String startCursor = encodeCursor(posts.get(0).getCreatedAt());
        String endCursor = encodeCursor(posts.get(posts.size() - 1).getCreatedAt());
        return CursorPageResponse.of(content, pageSize, startCursor, endCursor, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<FeedPostResponse> getFeed(UUID viewerId, String cursor, int size) {
        List<UUID> authorIds = socialService.getAcceptedFollowingExcludingBlocks(viewerId);
        int pageSize = normalizeLimit(size);
        if (authorIds.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
        }
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<Post> posts =
                cursorTime == null
                        ? postRepository.findFirstFeedPosts(authorIds, PostStatus.PUBLISHED, page)
                        : postRepository.findFeedPostsBefore(
                                authorIds, PostStatus.PUBLISHED, cursorTime, page);
        if (posts.size() > pageSize) {
            posts = posts.subList(0, pageSize);
        }
        if (posts.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
        }
        List<FeedPostResponse> content = postResponseAssembler.assembleFeed(posts);
        String startCursor = encodeCursor(posts.get(0).getCreatedAt());
        String endCursor = encodeCursor(posts.get(posts.size() - 1).getCreatedAt());
        return CursorPageResponse.of(content, pageSize, startCursor, endCursor, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostEditHistoryResponse> listEditHistory(
            UUID requesterId, UUID postId, String cursor, int size) {
        Post post =
                postRepository
                        .findByIdAndDeletedAtIsNull(postId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.POST_NOT_FOUND));
        if (!requesterId.equals(post.getUserId())) {
            throw new AppException(ApiErrorCode.POST_FORBIDDEN);
        }
        int pageSize = normalizeLimit(size);
        OffsetDateTime cursorTime = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<PostEditHistory> rows =
                cursorTime == null
                        ? postEditHistoryRepository.findFirstByPost(postId, page)
                        : postEditHistoryRepository.findByPostBefore(postId, cursorTime, page);
        if (rows.size() > pageSize) {
            rows = rows.subList(0, pageSize);
        }
        if (rows.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
        }
        List<PostEditHistoryResponse> content =
                rows.stream().map(postMapper::toEditHistoryResponse).toList();
        String startCursor = encodeCursor(rows.get(0).getEditedAt());
        String endCursor = encodeCursor(rows.get(rows.size() - 1).getEditedAt());
        return CursorPageResponse.of(content, pageSize, startCursor, endCursor, cursor != null);
    }

    // Soft delete keeps the row (GLOBAL_RULES soft delete policy): status flip + deleted_at; the
    // trg_post_count trigger adjusts users.post_count on the same UPDATE statement.
    private void softDelete(Post post) {
        post.setStatus(PostStatus.REMOVED);
        post.setDeletedAt(OffsetDateTime.now());
        hashtagService.removeHashtagsForPost(post.getId());
        enqueuePostIndexDelete(post);
    }

    private void validateMediaCardinality(
            PostType postType, List<UUID> mediaIds, Map<UUID, MediaAsset> assets) {
        if (postType == PostType.CAROUSEL) {
            if (mediaIds.size() < 2) {
                throw new AppException(
                        ApiErrorCode.BAD_REQUEST, "Carousel posts require at least 2 media items");
            }
            long maxItems = systemSettingService.getRequiredLong(MAX_POST_MEDIA_ITEMS);
            if (mediaIds.size() > maxItems) {
                throw new AppException(
                        ApiErrorCode.BAD_REQUEST,
                        "Carousel exceeds the maximum of " + maxItems + " media items");
            }
            return;
        }
        if (mediaIds.size() != 1) {
            throw new AppException(
                    ApiErrorCode.BAD_REQUEST, "Image and video posts require exactly 1 media item");
        }
        MediaAsset asset = assets.get(mediaIds.get(0));
        MediaType expected = postType == PostType.IMAGE ? MediaType.IMAGE : MediaType.VIDEO;
        if (asset.getMediaType() != expected) {
            throw new AppException(
                    ApiErrorCode.BAD_REQUEST, "Media asset type does not match the post type");
        }
    }

    private void upsertCaptionHashtags(UUID postId, String caption) {
        List<String> tags = extractHashtags(caption);
        if (!tags.isEmpty()) {
            hashtagService.upsertHashtagsForPost(postId, tags);
        }
    }

    // Must run after upsertCaptionHashtags so the post_hashtags associations are queryable here.
    private void enqueuePostIndexUpsert(Post post) {
        List<String> hashtagIds =
                hashtagService
                        .getHashtagIdsForPosts(List.of(post.getId()))
                        .getOrDefault(post.getId(), List.of())
                        .stream()
                        .map(UUID::toString)
                        .toList();
        // User free-text (caption) is excluded from the payload; the consumer reads it from the
        // source-of-truth post row it already loads for the Q4 gate.
        Map<String, Object> data = new HashMap<>();
        data.put("postId", post.getId().toString());
        data.put("userId", post.getUserId().toString());
        data.put("status", "published");
        data.put("hashtagIds", hashtagIds);
        data.put("createdAt", post.getCreatedAt().toString());
        outboxService.enqueue(
                PostEventTypes.POST_INDEX_UPSERT_V1,
                PostEventTypes.POST_INDEX_UPSERT_V1,
                "post",
                post.getId(),
                post.getUserId(),
                data);
    }

    private void enqueuePostIndexDelete(Post post) {
        outboxService.enqueue(
                PostEventTypes.POST_INDEX_DELETE_V1,
                PostEventTypes.POST_INDEX_DELETE_V1,
                "post",
                post.getId(),
                post.getUserId(),
                Map.of("postId", post.getId().toString()));
    }

    // Tokens are #-prefixed runs of Unicode letters, digits, and underscores; normalization and
    // case-insensitive de-duplication are HashtagService's responsibility.
    private List<String> extractHashtags(String caption) {
        if (caption == null || caption.isBlank()) {
            return List.of();
        }
        long maxTags = systemSettingService.getRequiredLong(MAX_HASHTAGS_PER_POST);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(caption);
        while (matcher.find() && tags.size() < maxTags) {
            tags.add(matcher.group(1));
        }
        return List.copyOf(tags);
    }

    // SecurityUtils.getCurrentUser throws when no principal is bound (system/test contexts);
    // absence of a principal must read as "not an admin", not as an authorization error.
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
}
