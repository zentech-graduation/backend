package com.app.modules.message.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;

/**
 * Module-local read-only repository over the post module's {@link Post} entity.
 *
 * <p>Mirrors the {@code StoryMediaAssetRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean. Used to
 * validate that a post-share message's referenced post exists.
 */
@Repository
public interface MessagePostRepository extends JpaRepository<Post, UUID> {

    /** True only for a post that exists, is not soft-deleted, and is in the given status. */
    boolean existsByIdAndDeletedAtIsNullAndStatus(UUID id, PostStatus status);
}
