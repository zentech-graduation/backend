package com.app.modules.post.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.media.entity.MediaAsset;
import com.app.modules.post.dto.response.LikerResponse;
import com.app.modules.post.dto.response.PostEditHistoryResponse;
import com.app.modules.post.dto.response.PostMediaResponse;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostEditHistory;
import com.app.modules.post.entity.PostMedia;
import com.app.modules.post.search.PostDocument;
import com.app.modules.users.entity.User;

/** Maps post entities to API response DTOs. */
@Mapper(componentModel = "spring")
public interface PostMapper {

    /**
     * Builds the post response from the entity and the separately hydrated media items.
     *
     * @param post the source post; media ordering comes from the entity collection {@code @OrderBy}
     * @param media media responses already joined with their {@code media_assets} rows
     * @return the post response with media populated
     */
    @Mapping(source = "media", target = "media")
    PostResponse toResponse(Post post, List<PostMediaResponse> media);

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

    PostEditHistoryResponse toEditHistoryResponse(PostEditHistory history);

    /**
     * Projects a user row onto the liker summary shape.
     *
     * @param user the liking user
     * @return the liker summary
     */
    @Mapping(source = "id", target = "userId")
    @Mapping(source = "verified", target = "isVerified")
    LikerResponse toLikerResponse(User user);

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
