package com.app.modules.post.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.media.entity.MediaAsset;
import com.app.modules.post.dto.response.FeedPostResponse;
import com.app.modules.post.dto.response.PostEditHistoryResponse;
import com.app.modules.post.dto.response.PostMediaResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostEditHistory;
import com.app.modules.post.entity.PostMedia;
import com.app.modules.post.search.PostDocument;

/** Maps post entities to API response DTOs. */
@Mapper(componentModel = "spring")
public interface PostMapper {

    /**
     * Builds the post response from the entity, the separately hydrated media items, and the post's
     * author.
     *
     * @param post the source post; media ordering comes from the entity collection {@code @OrderBy}
     * @param media media responses already joined with their {@code media_assets} rows
     * @param author the post author's public summary, batch-resolved by the service
     * @return the post response with media and the embedded author
     */
    @Mapping(source = "post.id", target = "id")
    @Mapping(source = "post.status", target = "status")
    @Mapping(source = "post.createdAt", target = "createdAt")
    @Mapping(source = "post.updatedAt", target = "updatedAt")
    @Mapping(source = "media", target = "media")
    @Mapping(source = "author", target = "author")
    PostResponse toResponse(Post post, List<PostMediaResponse> media, UserSummaryResponse author);

    /**
     * Builds the feed-specific post response from the entity, the separately hydrated media items,
     * and the post's author, with {@code rankingScore} always null for the current chronological
     * implementation.
     *
     * @param post the source post; media ordering comes from the entity collection {@code @OrderBy}
     * @param media media responses already joined with their {@code media_assets} rows
     * @param author the post author's public summary, batch-resolved by the service
     * @return the feed post response with media and the embedded author, ranking score reserved as
     *     null
     */
    @Mapping(source = "post.id", target = "id")
    @Mapping(source = "post.status", target = "status")
    @Mapping(source = "post.createdAt", target = "createdAt")
    @Mapping(source = "post.updatedAt", target = "updatedAt")
    @Mapping(source = "media", target = "media")
    @Mapping(source = "author", target = "author")
    @Mapping(target = "rankingScore", ignore = true)
    FeedPostResponse toFeedResponse(
            Post post, List<PostMediaResponse> media, UserSummaryResponse author);

    /**
     * Combines a post media row with its referenced media asset for rendering.
     *
     * @param postMedia the ordered media row
     * @param asset the media module asset providing CDN URL and dimensions
     * @return the merged media response
     */
    @Mapping(source = "postMedia.id", target = "id")
    @Mapping(source = "postMedia.mediaAssetId", target = "mediaAssetId")
    @Mapping(source = "postMedia.position", target = "position")
    @Mapping(source = "postMedia.altText", target = "altText")
    @Mapping(source = "asset.cdnUrl", target = "cdnUrl")
    @Mapping(source = "asset.mediaType", target = "mediaType")
    @Mapping(source = "asset.width", target = "width")
    @Mapping(source = "asset.height", target = "height")
    @Mapping(source = "asset.blurhash", target = "blurhash")
    PostMediaResponse toMediaResponse(PostMedia postMedia, MediaAsset asset);

    /**
     * Builds a caption edit history entry with the editor's public summary embedded.
     *
     * @param history the append-only audit row
     * @param editor the editing user's public summary, batch-resolved by the service
     * @return the edit history response with the embedded editor
     */
    @Mapping(source = "history.id", target = "id")
    @Mapping(source = "editor", target = "editor")
    PostEditHistoryResponse toEditHistoryResponse(
            PostEditHistory history, UserSummaryResponse editor);

    /**
     * Projects the entity to its Elasticsearch document, converting UUIDs to string form and the
     * status to its lowercase index value.
     *
     * @param post the source post
     * @param hashtagIds string-form hashtag ids resolved from {@code post_hashtags}
     * @return the indexable document
     */
    @Mapping(target = "id", expression = "java(post.getId().toString())")
    @Mapping(target = "userId", expression = "java(post.getUserId().toString())")
    @Mapping(
            target = "status",
            expression = "java(post.getStatus().name().toLowerCase(java.util.Locale.ROOT))")
    @Mapping(source = "hashtagIds", target = "hashtagIds")
    @Mapping(source = "post.caption", target = "caption")
    @Mapping(source = "post.createdAt", target = "createdAt")
    PostDocument toDocument(Post post, List<String> hashtagIds);
}
