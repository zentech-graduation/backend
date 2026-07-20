package com.app.modules.story.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.app.modules.story.entity.StoryView;
import com.app.modules.story.entity.StoryViewId;

/** Persistence access for {@link StoryView}. */
@Repository
public interface StoryViewRepository extends JpaRepository<StoryView, StoryViewId> {

    /**
     * Records a view, silently ignoring duplicates via the composite primary key (DATA_RULES §3B).
     *
     * @return 1 when a new row was inserted, 0 on a repeat view
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO story_views (story_id, viewer_id) VALUES (:storyId, :viewerId)"
                            + " ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIgnoringDuplicate(UUID storyId, UUID viewerId);

    /** Story ids among {@code storyIds} that the viewer has already seen (feed seen-flags). */
    @Query(
            "SELECT sv.id.storyId FROM StoryView sv WHERE sv.id.viewerId = :viewerId"
                    + " AND sv.id.storyId IN :storyIds")
    List<UUID> findViewedStoryIds(UUID viewerId, List<UUID> storyIds);

    /** First page of a story's viewer list, newest view first. */
    @Query(
            "SELECT sv FROM StoryView sv WHERE sv.id.storyId = :storyId"
                    + " ORDER BY sv.viewedAt DESC, sv.id.viewerId DESC")
    List<StoryView> findFirstViewers(UUID storyId, Pageable pageable);

    /** Keyset continuation of the viewer list after the {@code (viewedAt, viewerId)} cursor. */
    @Query(
            "SELECT sv FROM StoryView sv WHERE sv.id.storyId = :storyId"
                    + " AND (sv.viewedAt < :cursorTime"
                    + " OR (sv.viewedAt = :cursorTime AND sv.id.viewerId < :cursorId))"
                    + " ORDER BY sv.viewedAt DESC, sv.id.viewerId DESC")
    List<StoryView> findViewersBefore(
            UUID storyId, OffsetDateTime cursorTime, UUID cursorId, Pageable pageable);
}
