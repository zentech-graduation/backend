package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.PostLike;
import com.app.modules.post.entity.PostLikeId;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    /**
     * First keyset page of likes for a post, newest first.
     *
     * <p>Paired with {@link #findLikersBefore}; the no-cursor variant avoids binding an untyped
     * null cursor. Native so the sibling can use a row-value tuple comparison for an exact index
     * seek, which JPQL cannot express. The tiebreaker is {@code user_id}, the unique like key
     * within a post.
     *
     * @param postId liked post
     * @param pageable page size carrier
     * @return likes ordered by the {@code (created_at, user_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_likes WHERE post_id = :postId "
                            + "ORDER BY created_at DESC, user_id DESC",
            nativeQuery = true)
    List<PostLike> findFirstLikers(@Param("postId") UUID postId, Pageable pageable);

    /**
     * Keyset page of likes for a post strictly after the cursor tuple, newest first.
     *
     * <p>The {@code (created_at, user_id)} row-value comparison seeks directly to the cursor
     * position and never drops likes sharing a boundary {@code created_at}. Served exactly by
     * {@code idx_post_likes_post_created_user} (V35).
     *
     * @param postId liked post
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorUserId liker id of the cursor row, breaking ties on equal {@code created_at}
     * @param pageable page size carrier
     * @return likes ordered by the {@code (created_at, user_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_likes WHERE post_id = :postId "
                            + "AND (created_at, user_id) < (:cursorTime, :cursorUserId) "
                            + "ORDER BY created_at DESC, user_id DESC",
            nativeQuery = true)
    List<PostLike> findLikersBefore(
            @Param("postId") UUID postId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable);
}
