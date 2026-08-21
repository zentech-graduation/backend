package com.app.modules.post.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.app.modules.post.entity.Post;

/** Account-level visibility decisions for posts. */
public interface PostVisibilityService {

    /**
     * Decides whether the viewer may see the given post based on account-level state.
     *
     * <p>The owner always sees their own posts. A block in either direction hides the post. A
     * private owner account requires the viewer to hold an accepted follow. A soft-deleted owner
     * hides all of their content. Post status gating (draft, archived) is the caller's concern.
     *
     * @param viewerId authenticated viewer
     * @param post the post whose owner's account state is evaluated
     * @return true when the post is visible to the viewer
     */
    boolean isVisibleTo(UUID viewerId, Post post);

    /**
     * Batch form of {@link #isVisibleTo(UUID, Post)} for many candidate post owners at once.
     *
     * <p>Applies the same account-level rules (owner, block, private-account follow, soft-delete)
     * but issues a fixed number of queries regardless of how many owner ids are passed in, unlike
     * calling {@link #isVisibleTo(UUID, Post)} once per candidate. Intended for ranking or feed
     * pipelines that must filter a page of candidates from an external or unordered source.
     *
     * @param viewerId authenticated viewer
     * @param ownerIds candidate post-owner ids to evaluate; duplicates are tolerated
     * @return the subset of {@code ownerIds} whose content is visible to the viewer
     */
    Set<UUID> filterVisibleOwnerIds(UUID viewerId, Collection<UUID> ownerIds);
}
