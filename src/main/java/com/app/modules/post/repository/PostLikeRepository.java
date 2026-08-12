package com.app.modules.post.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.PostLike;
import com.app.modules.post.entity.PostLikeId;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    /**
     * Post ids among {@code postIds} that the viewer has liked, for batched {@code isLiked} flags.
     *
     * @param viewerId the requesting viewer
     * @param postIds candidate post ids on the current page
     * @return the subset the viewer has liked
     */
    @Query(
            "SELECT pl.id.postId FROM PostLike pl"
                    + " WHERE pl.id.userId = :viewerId AND pl.id.postId IN :postIds")
    List<UUID> findLikedPostIds(
            @Param("viewerId") UUID viewerId, @Param("postIds") Collection<UUID> postIds);

    /**
     * Deletes a single like, returning the affected-row count so the caller can distinguish an
     * actual unlike from a no-op instead of loading the row first and calling {@code
     * delete(entity)}, which raises {@link
     * org.springframework.orm.ObjectOptimisticLockingFailureException} when a concurrent request
     * already removed the same row.
     *
     * @param userId user removing the like
     * @param postId post being unliked
     * @return number of rows deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.id.userId = :userId AND pl.id.postId = :postId")
    int deleteByUserAndPost(@Param("userId") UUID userId, @Param("postId") UUID postId);

    /**
     * First keyset page of likes for a post, newest first, excluding any liker in a block
     * relationship with the viewer.
     *
     * <p>Paired with {@link #findLikersBefore}; the no-cursor variant avoids binding an untyped
     * null cursor. Native so the sibling can use a row-value tuple comparison for an exact index
     * seek, which JPQL cannot express. The tiebreaker is {@code user_id}, the unique like key
     * within a post.
     *
     * <p>The block exclusion is bidirectional: the stealth block model requires a liker who has
     * blocked the viewer, or whom the viewer has blocked, to be absent from this list rather than
     * merely unflagged.
     *
     * @param postId liked post
     * @param viewerId the requesting viewer; likers in a block relationship with this user are
     *     excluded
     * @param pageable page size carrier
     * @return likes ordered by the {@code (created_at, user_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_likes WHERE post_id = :postId "
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = post_likes.user_id)"
                            + " OR (b.blocker_id = post_likes.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, user_id DESC",
            nativeQuery = true)
    List<PostLike> findFirstLikers(
            @Param("postId") UUID postId, @Param("viewerId") UUID viewerId, Pageable pageable);

    /**
     * Keyset page of likes for a post strictly after the cursor tuple, newest first, excluding any
     * liker in a block relationship with the viewer.
     *
     * <p>The {@code (created_at, user_id)} row-value comparison seeks directly to the cursor
     * position and never drops likes sharing a boundary {@code created_at}. Served exactly by
     * {@code idx_post_likes_post_created_user} (V35); the block exclusion joins on {@code
     * idx_blocks_blocker} / {@code idx_blocks_blocked} (V15). See {@link #findFirstLikers} for why
     * the exclusion is bidirectional.
     *
     * @param postId liked post
     * @param viewerId the requesting viewer; likers in a block relationship with this user are
     *     excluded
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorUserId liker id of the cursor row, breaking ties on equal {@code created_at}
     * @param pageable page size carrier
     * @return likes ordered by the {@code (created_at, user_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_likes WHERE post_id = :postId "
                            + "AND (created_at, user_id) < (:cursorTime, :cursorUserId) "
                            + "AND NOT EXISTS (SELECT 1 FROM blocks b"
                            + " WHERE (b.blocker_id = :viewerId AND b.blocked_id = post_likes.user_id)"
                            + " OR (b.blocker_id = post_likes.user_id AND b.blocked_id = :viewerId)) "
                            + "ORDER BY created_at DESC, user_id DESC",
            nativeQuery = true)
    List<PostLike> findLikersBefore(
            @Param("postId") UUID postId,
            @Param("viewerId") UUID viewerId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorUserId") UUID cursorUserId,
            Pageable pageable);

    /**
     * First keyset page of the posts one user has liked, newest like first.
     *
     * <p>Paired with {@link #findLikesBefore}; the no-cursor variant avoids binding an untyped null
     * cursor. Native so the sibling can use a row-value tuple comparison for an exact index seek,
     * which JPQL cannot express. The tiebreaker is {@code post_id}, the unique like key within a
     * user.
     *
     * @param userId the liking user
     * @param pageable page size carrier (page number is always 0 for keyset paging)
     * @return likes ordered by the {@code (created_at, post_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_likes WHERE user_id = :userId "
                            + "ORDER BY created_at DESC, post_id DESC",
            nativeQuery = true)
    List<PostLike> findFirstLikes(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Keyset page of one user's likes strictly after the cursor tuple, newest like first.
     *
     * <p>The {@code (created_at, post_id)} row-value comparison seeks directly to the cursor
     * position and never drops likes sharing a boundary {@code created_at}. Served exactly by
     * {@code idx_post_likes_user_created_post} (V46).
     *
     * @param userId the liking user
     * @param cursorTime {@code created_at} of the cursor row; never null
     * @param cursorPostId liked-post id of the cursor row, breaking ties on equal {@code
     *     created_at}
     * @param pageable page size carrier
     * @return likes ordered by the {@code (created_at, post_id)} tuple descending
     */
    @Query(
            value =
                    "SELECT * FROM post_likes WHERE user_id = :userId "
                            + "AND (created_at, post_id) < (:cursorTime, :cursorPostId) "
                            + "ORDER BY created_at DESC, post_id DESC",
            nativeQuery = true)
    List<PostLike> findLikesBefore(
            @Param("userId") UUID userId,
            @Param("cursorTime") OffsetDateTime cursorTime,
            @Param("cursorPostId") UUID cursorPostId,
            Pageable pageable);
}
