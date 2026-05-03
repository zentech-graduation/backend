package com.app.common.security;

/**
 * Marks revoked access tokens so that {@link JwtAuthenticationFilter} can reject them before the
 * natural {@code exp} claim elapses.
 */
public interface TokenBlacklistService {

    /**
     * Blacklists the given JWT id for the supplied remaining lifetime. No-op when the token has
     * already expired.
     *
     * @param jti the {@code jti} claim of the token to revoke
     * @param remainingTtlSeconds seconds until the token's natural expiry
     */
    void blacklist(String jti, long remainingTtlSeconds);

    /**
     * Returns whether the given JWT id has been blacklisted.
     *
     * @param jti the {@code jti} claim to check
     * @return {@code true} when present in the blacklist, {@code false} otherwise
     */
    boolean isBlacklisted(String jti);
}
