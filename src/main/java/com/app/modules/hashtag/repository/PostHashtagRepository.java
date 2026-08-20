package com.app.modules.hashtag.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.hashtag.entity.PostHashtag;
import com.app.modules.hashtag.entity.PostHashtagId;
import com.app.modules.hashtag.enums.HashtagStatus;

@Repository
public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtagId> {

    /**
     * Removes all hashtag associations for a post.
     *
     * <p>Triggers {@code trg_hashtag_post_count} on each deleted row, decrementing the affected
     * {@code hashtags.post_count}.
     *
     * @param postId the post whose hashtag associations are removed
     */
    @Modifying
    @Query("DELETE FROM PostHashtag ph WHERE ph.id.postId = :postId")
    void deleteAllByPostId(@Param("postId") UUID postId);

    /**
     * Returns the hashtag ids associated with the given post.
     *
     * @param postId the post whose hashtag ids are returned
     * @return the associated hashtag ids; empty when the post has no hashtags
     */
    @Query("SELECT ph.id.hashtagId FROM PostHashtag ph WHERE ph.id.postId = :postId")
    List<UUID> findHashtagIdsByPostId(@Param("postId") UUID postId);

    List<PostHashtag> findAllByIdPostIdIn(Collection<UUID> postIds);

    /**
     * Returns the hashtag associations of the given posts, narrowed to the supplied statuses and
     * carrying the hashtag name.
     *
     * <p>One statement for a whole page of posts, so hydrating a listing costs no query per row.
     * The status filter is what keeps a deleted hashtag off a post response while leaving its
     * {@code post_hashtags} row in place, so nothing is lost if the tag is ever restored.
     *
     * @param postIds the posts to hydrate
     * @param statuses hashtag statuses eligible to appear on a post
     * @return associations ordered by hashtag name; posts without eligible hashtags are absent
     */
    @Query(
            "SELECT ph.id.postId AS postId, h.id AS hashtagId, h.name AS name"
                    + " FROM PostHashtag ph, Hashtag h"
                    + " WHERE h.id = ph.id.hashtagId AND ph.id.postId IN :postIds"
                    + " AND h.status IN :statuses"
                    + " ORDER BY h.name ASC")
    List<PostHashtagNameProjection> findNamedByPostIdIn(
            @Param("postIds") Collection<UUID> postIds,
            @Param("statuses") Collection<HashtagStatus> statuses);
}
