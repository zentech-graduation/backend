package com.app.modules.social.repository;

import java.util.UUID;

/**
 * One directed follow edge between the viewer and a candidate user, returned by {@link
 * FollowRepository#findRelationshipEdges}.
 *
 * <p>{@code outgoing} is {@code true} when the viewer is the follower (viewer -&gt; other) and
 * {@code false} when the viewer is being followed (other -&gt; viewer).
 */
public interface FollowEdgeProjection {

    UUID getOtherId();

    String getStatus();

    boolean getOutgoing();
}
