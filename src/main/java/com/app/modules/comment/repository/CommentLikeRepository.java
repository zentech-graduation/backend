package com.app.modules.comment.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.comment.entity.CommentLike;
import com.app.modules.comment.entity.CommentLikeId;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {

    boolean existsByIdUserIdAndIdCommentId(UUID userId, UUID commentId);

    /**
     * Comment ids among {@code commentIds} that the viewer has liked, for a batched {@code isLiked}
     * flag.
     *
     * @param viewerId the requesting viewer
     * @param commentIds candidate comment ids on the current page
     * @return the subset the viewer has liked
     */
    @Query(
            "SELECT cl.id.commentId FROM CommentLike cl"
                    + " WHERE cl.id.userId = :viewerId AND cl.id.commentId IN :commentIds")
    List<UUID> findLikedCommentIds(
            @Param("viewerId") UUID viewerId, @Param("commentIds") Collection<UUID> commentIds);

    /**
     * Deletes a single like, returning whether a row was removed so the caller can distinguish an
     * actual unlike from a no-op.
     *
     * @param userId user removing the like
     * @param commentId comment being unliked
     * @return number of rows deleted (0 or 1)
     */
    @Modifying
    @Query(
            "DELETE FROM CommentLike cl WHERE cl.id.userId = :userId "
                    + "AND cl.id.commentId = :commentId")
    int deleteByUserAndComment(@Param("userId") UUID userId, @Param("commentId") UUID commentId);
}
