package com.app.modules.post.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.dto.response.PostMediaResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostMedia;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.repository.PostMediaAssetRepository;
import com.app.modules.post.service.PostViewerState;
import com.app.modules.post.service.PostViewerStateService;
import com.app.modules.users.service.UserSummaryService;

/**
 * Assembles {@link PostResponse} DTOs by joining posts with their {@code media_assets} rows, author
 * {@code users} rows, and the requesting viewer's like/save state.
 *
 * <p>Shared by the post, save, and search services so media, author, and viewer-state hydration
 * stay in one place.
 */
@Component
public class PostResponseAssembler {

    private final PostMediaAssetRepository postMediaAssetRepository;
    private final UserSummaryService userSummaryService;
    private final PostViewerStateService postViewerStateService;
    private final PostMapper postMapper;

    public PostResponseAssembler(
            PostMediaAssetRepository postMediaAssetRepository,
            UserSummaryService userSummaryService,
            PostViewerStateService postViewerStateService,
            PostMapper postMapper) {
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.userSummaryService = userSummaryService;
        this.postViewerStateService = postViewerStateService;
        this.postMapper = postMapper;
    }

    public PostResponse assemble(UUID viewerId, Post post) {
        return assemble(viewerId, List.of(post)).get(0);
    }

    public List<PostResponse> assemble(UUID viewerId, List<Post> posts) {
        // Single batched asset lookup avoids one media_assets query per post on list pages.
        Set<UUID> assetIds =
                posts.stream()
                        .flatMap(p -> p.getMedia().stream())
                        .map(PostMedia::getMediaAssetId)
                        .collect(Collectors.toSet());
        Map<UUID, MediaAsset> assets =
                assetIds.isEmpty()
                        ? Map.of()
                        : postMediaAssetRepository.findAllById(assetIds).stream()
                                .collect(Collectors.toMap(MediaAsset::getId, a -> a));
        Map<UUID, UserSummaryResponse> authors = batchFetchAuthors(posts);
        PostViewerState viewerState = batchFetchViewerState(viewerId, posts);
        List<PostResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            List<PostMediaResponse> media =
                    post.getMedia().stream()
                            .map(
                                    pm ->
                                            postMapper.toMediaResponse(
                                                    pm, assets.get(pm.getMediaAssetId())))
                            .toList();
            result.add(
                    postMapper.toResponse(
                            post,
                            media,
                            authors.get(post.getUserId()),
                            viewerState.isLiked(post.getId()),
                            viewerState.isSaved(post.getId())));
        }
        return result;
    }

    /**
     * Assembles {@link FeedPostResponse} DTOs for the following feed.
     *
     * <p>Batches the {@code media_assets} lookup identically to {@link #assemble(UUID, List)} to
     * avoid per-post queries on list pages.
     *
     * @param viewerId the requesting viewer, whose like/save state is batch-resolved
     * @param posts posts to assemble; must not be empty
     * @return feed post responses in the same order as the input list
     */
    public List<FeedPostResponse> assembleFeed(UUID viewerId, List<Post> posts) {
        Set<UUID> assetIds =
                posts.stream()
                        .flatMap(p -> p.getMedia().stream())
                        .map(PostMedia::getMediaAssetId)
                        .collect(Collectors.toSet());
        Map<UUID, MediaAsset> assets =
                assetIds.isEmpty()
                        ? Map.of()
                        : postMediaAssetRepository.findAllById(assetIds).stream()
                                .collect(Collectors.toMap(MediaAsset::getId, a -> a));
        Map<UUID, UserSummaryResponse> authors = batchFetchAuthors(posts);
        PostViewerState viewerState = batchFetchViewerState(viewerId, posts);
        List<FeedPostResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            List<PostMediaResponse> media =
                    post.getMedia().stream()
                            .map(
                                    pm ->
                                            postMapper.toMediaResponse(
                                                    pm, assets.get(pm.getMediaAssetId())))
                            .toList();
            result.add(
                    postMapper.toFeedResponse(
                            post,
                            media,
                            authors.get(post.getUserId()),
                            viewerState.isLiked(post.getId()),
                            viewerState.isSaved(post.getId())));
        }
        return result;
    }

    // One batched summary lookup for every author on the page; a soft-deleted author resolves to a
    // placeholder so a post never renders without an author object.
    private Map<UUID, UserSummaryResponse> batchFetchAuthors(List<Post> posts) {
        return userSummaryService.loadSummaries(posts.stream().map(Post::getUserId).toList());
    }

    // One batched like/save lookup for every post on the page instead of one probe per row.
    private PostViewerState batchFetchViewerState(UUID viewerId, List<Post> posts) {
        return postViewerStateService.load(viewerId, posts.stream().map(Post::getId).toList());
    }
}
