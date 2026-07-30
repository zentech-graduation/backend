package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.PostSave;
import com.app.modules.post.entity.PostSaveId;

@Repository
public interface PostSaveRepository extends JpaRepository<PostSave, PostSaveId> {

    /**
     * Post ids among {@code postIds} that the viewer has saved, for batched {@code isSaved} flags.
     *
     * @param viewerId the requesting viewer
     * @param postIds candidate post ids on the current page
     * @return the subset the viewer has saved
     */
    @Query(
            "SELECT ps.id.postId FROM PostSave ps"
                    + " WHERE ps.id.userId = :viewerId AND ps.id.postId IN :postIds")
    List<UUID> findSavedPostIds(
            @Param("viewerId") UUID viewerId, @Param("postIds") Collection<UUID> postIds);

    /**
     * First keyset page of a user's saves, newest first.
     *
     * <p>Paired with {@link #findSavesBefore}; the no-cursor variant avoids binding an untyped null
     * cursor. Native so the sibling can use a row-value tuple comparison for an exact index seek,
     * which JPQL cannot express. The tiebreaker is {@code post_id}, the unique save key within a
     * user.
     *
     * @param userId saving user
     * @param pageable page size carrier
     * @return saves ordered by the {@code (created_at, post_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_saves WHERE user_id = :userId "
                            + "ORDER BY created_at DESC, post_id DESC",
            nativeQuery = true)
    List<PostSave> findFirstSaves(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Keyset page of a user's saves strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, post_id)} row-value comparison seeks directly to the cursor
     * position and never drops saves sharing a boundary {@code created_at}. Served exactly by
     * {@code idx_post_saves_user_created_post} (V35).
     *
     * @param userId saving user
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorPostId saved-post id of the cursor row, breaking ties on equal {@code
     *     created_at}
     * @param pageable page size carrier
     * @return saves ordered by the {@code (created_at, post_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_saves WHERE user_id = :userId "
                            + "AND (created_at, post_id) < (:cursorTime, :cursorPostId) "
                            + "ORDER BY created_at DESC, post_id DESC",
            nativeQuery = true)
    List<PostSave> findSavesBefore(
            @Param("userId") UUID userId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorPostId") UUID cursorPostId,
            Pageable pageable);
}
