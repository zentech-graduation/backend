package com.app.common.security.jwt;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of the claims the application cares about after JWT validation.
 *
 * <p>{@code tokenEpoch} is null for a token minted before the claim existed. Readers must treat a
 * null as zero, which is the {@code users.token_epoch} column default, so tokens in flight when the
 * claim was introduced stay valid.
 */
public record JwtClaims(
        UUID userId, String role, String jti, Integer tokenEpoch, Instant expiresAt) {}
