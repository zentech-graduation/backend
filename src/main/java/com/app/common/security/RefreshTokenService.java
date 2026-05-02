package com.app.common.security;

import java.util.UUID;

/**
 * Issuance, rotation, and revocation of opaque refresh tokens.
 *
 * <p>Refresh tokens are random UUID strings. Only the SHA-256 hash is persisted; the raw value is
 * returned to the caller exactly once at issuance and from {@link #rotate(String, String)}.
 */
public interface RefreshTokenService {

    /**
     * Issues a new refresh token bound to the given user and device metadata.
     *
     * @param userId owner of the token
     * @param deviceId optional opaque device identifier
     * @param userAgent optional user agent string
     * @param ipAddress optional source IP address
     * @return the raw refresh token to send to the client
     */
    String issue(UUID userId, String deviceId, String userAgent, String ipAddress);

    /**
     * Atomically revokes the supplied raw token and issues a fresh one for the same user.
     *
     * @param rawToken raw refresh token presented by the client
     * @param ipAddress source IP of the rotation request
     * @return the rotation result containing the new raw token and its owning user id
     * @throws com.app.common.exception.AppException with {@code AUTH_REFRESH_TOKEN_INVALID} when
     *     the token does not exist or is already revoked, or {@code AUTH_REFRESH_TOKEN_EXPIRED}
     *     when the token has passed its expiry
     */
    RotationResult rotate(String rawToken, String ipAddress);

    /**
     * Revokes the supplied raw token. No-op when the token does not exist, allowing logout to be
     * idempotent.
     *
     * @param rawToken raw refresh token to revoke
     */
    void revoke(String rawToken);

    /**
     * Revokes every active refresh token belonging to the given user.
     *
     * @param userId owner whose sessions should be terminated
     */
    void revokeAllForUser(UUID userId);

    record RotationResult(String newRawToken, UUID userId) {}
}
