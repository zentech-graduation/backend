package com.app.common.security.jwt;

import java.time.Instant;
import java.util.UUID;

/** Snapshot of the claims the application cares about after JWT validation. */
public record JwtClaims(UUID userId, String role, String jti, Instant expiresAt) {}
