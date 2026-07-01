package com.app.modules.post.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.app.modules.media.entity.MediaAsset;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.dto.response.PostMediaResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostMedia;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.repository.PostMediaAssetRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.repository.UserRepository;

/**
 * Assembles {@link PostResponse} DTOs by joining posts with their {@code media_assets} rows and
 * author {@code users} rows.
 *
 * <p>Shared by the post, save, and search services so media and author hydration stay in one place.
 */
@Component
public class PostResponseAssembler {

    private final PostMediaAssetRepository postMediaAssetRepository;
    private final UserRepository userRepository;
    private final PostMapper postMapper;

    public PostResponseAssembler(
            PostMediaAssetRepository postMediaAssetRepository,
            UserRepository userRepository,
            PostMapper postMapper) {
        this.postMediaAssetRepository = postMediaAssetRepository;
        this.userRepository = userRepository;
        this.postMapper = postMapper;
    }

    public PostResponse assemble(Post post) {
        return assemble(List.of(post)).get(0);
    }

    public List<PostResponse> assemble(List<Post> posts) {
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
        Map<UUID, User> authors = batchFetchAuthors(posts);
        List<PostResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            List<PostMediaResponse> media =
                    post.getMedia().stream()
                            .map(
                                    pm ->
                                            postMapper.toMediaResponse(
                                                    pm, assets.get(pm.getMediaAssetId())))
                            .toList();
            result.add(postMapper.toResponse(post, media, authors.get(post.getUserId())));
        }
        return result;
    }

    /**
     * Assembles {@link FeedPostResponse} DTOs for the following feed.
     *
     * <p>Batches the {@code media_assets} lookup identically to {@link #assemble(List)} to avoid
     * per-post queries on list pages.
     *
     * @param posts posts to assemble; must not be empty
     * @return feed post responses in the same order as the input list
     */
    public List<FeedPostResponse> assembleFeed(List<Post> posts) {
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
        Map<UUID, User> authors = batchFetchAuthors(posts);
        List<FeedPostResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            List<PostMediaResponse> media =
                    post.getMedia().stream()
                            .map(
                                    pm ->
                                            postMapper.toMediaResponse(
                                                    pm, assets.get(pm.getMediaAssetId())))
                            .toList();
            result.add(postMapper.toFeedResponse(post, media, authors.get(post.getUserId())));
        }
        return result;
    }

    private Map<UUID, User> batchFetchAuthors(List<Post> posts) {
        Set<UUID> authorIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        return authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
    }
}
