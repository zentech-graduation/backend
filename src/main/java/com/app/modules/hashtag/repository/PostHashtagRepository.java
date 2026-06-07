package com.app.modules.hashtag.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.hashtag.entity.PostHashtag;
import com.app.modules.hashtag.entity.PostHashtagId;

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
}
