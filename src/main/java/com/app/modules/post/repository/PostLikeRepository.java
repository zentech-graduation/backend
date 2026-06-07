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
     * null timestamp, which PostgreSQL cannot type-infer.
     *
     * @param postId liked post
     * @param pageable page size carrier
     * @return likes ordered by {@code created_at} descending
     */
    @Query("SELECT pl FROM PostLike pl WHERE pl.id.postId = :postId ORDER BY pl.createdAt DESC")
    List<PostLike> findFirstLikers(@Param("postId") UUID postId, Pageable pageable);

    /**
     * Keyset page of likes for a post older than the cursor, newest first.
     *
     * @param postId liked post
     * @param cursor exclusive upper bound on {@code created_at}; never null
     * @param pageable page size carrier
     * @return likes ordered by {@code created_at} descending
     */
    @Query(
            "SELECT pl FROM PostLike pl WHERE pl.id.postId = :postId "
                    + "AND pl.createdAt < :cursor "
                    + "ORDER BY pl.createdAt DESC")
    List<PostLike> findLikersBefore(
            @Param("postId") UUID postId,
            @Param("cursor") OffsetDateTime cursor,
            Pageable pageable);
}
