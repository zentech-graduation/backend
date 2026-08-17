package com.app.modules.story.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.story.entity.StoryLike;
import com.app.modules.story.entity.StoryLikeId;

/** Persistence access for {@link StoryLike}. */
@Repository
public interface StoryLikeRepository extends JpaRepository<StoryLike, StoryLikeId> {

    /** Story ids among {@code storyIds} that the viewer has already liked. */
    @Query(
            "SELECT sl.id.storyId FROM StoryLike sl WHERE sl.id.userId = :viewerId"
                    + " AND sl.id.storyId IN :storyIds")
    List<UUID> findLikedStoryIds(
            @Param("viewerId") UUID viewerId, @Param("storyIds") List<UUID> storyIds);

    @Modifying
    @Query("DELETE FROM StoryLike sl WHERE sl.id.userId = :userId AND sl.id.storyId = :storyId")
    int deleteByUserAndStory(@Param("userId") UUID userId, @Param("storyId") UUID storyId);
}
