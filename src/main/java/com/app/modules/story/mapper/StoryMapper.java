package com.app.modules.story.mapper;

import java.time.OffsetDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.media.entity.MediaAsset;
import com.app.modules.story.dto.response.StoryMediaResponse;
import com.app.modules.story.dto.response.StoryResponse;
import com.app.modules.story.dto.response.StoryViewerResponse;
import com.app.modules.story.entity.Story;
import com.app.modules.users.entity.User;

/** Maps story entities to API response DTOs. */
@Mapper(componentModel = "spring")
public interface StoryMapper {

    /**
     * Builds the story response from the entity, its hydrated media asset, and the owner.
     *
     * @param story the source story
     * @param media the response for the story's single media asset
     * @param author the story owner, separately hydrated; author fields are null if the lookup
     *     missed
     * @param viewCount unique-viewer count, populated only when the requester is the owner
     * @param seen whether the requester has viewed the story; null when the requester is the owner
     * @return the story response with media, author, and viewer-context fields populated
     */
    @Mapping(source = "story.id", target = "id")
    @Mapping(source = "story.userId", target = "userId")
    @Mapping(source = "story.caption", target = "caption")
    @Mapping(source = "story.createdAt", target = "createdAt")
    @Mapping(source = "media", target = "media")
    @Mapping(source = "author.username", target = "username")
    @Mapping(source = "author.displayName", target = "userDisplayName")
    @Mapping(source = "author.avatarUrl", target = "userAvatarUrl")
    @Mapping(source = "viewCount", target = "viewCount")
    @Mapping(source = "seen", target = "seen")
    StoryResponse toResponse(
            Story story, StoryMediaResponse media, User author, Integer viewCount, Boolean seen);

    /**
     * Projects a media asset onto the story media shape.
     *
     * @param asset the backing media asset
     * @return the story media response
     */
    @Mapping(source = "id", target = "mediaAssetId")
    StoryMediaResponse toMediaResponse(MediaAsset asset);

    /**
     * Projects a viewer's user row and view timestamp onto the story viewer summary shape.
     *
     * @param user the viewing user
     * @param viewedAt when the view was recorded
     * @return the viewer summary
     */
    @Mapping(source = "user.id", target = "viewerId")
    StoryViewerResponse toViewerResponse(User user, OffsetDateTime viewedAt);
}
