package com.app.modules.social.repository;

import java.util.UUID;

/**
 * One directed block edge between the viewer and a candidate user, returned by {@link
 * BlockRepository#findRelationshipEdges}.
 *
 * <p>{@code outgoing} is {@code true} when the viewer is the blocker (viewer blocked other) and
 * {@code false} when the viewer is the blocked party (other blocked viewer).
 */
public interface BlockEdgeProjection {

    UUID getOtherId();

    boolean getOutgoing();
}
