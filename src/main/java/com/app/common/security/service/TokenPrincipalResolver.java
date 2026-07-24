package com.app.common.security.service;

import java.util.Optional;

import com.app.common.security.user.UserPrincipal;

/**
 * Resolves a raw JWT access token into an authenticated {@link UserPrincipal}, applying the full
 * set of checks a caller must pass before being treated as authenticated: signature, issuer, and
 * audience validity, expiry, blacklist status, and the account's current status.
 *
 * <p>Shared by {@link com.app.common.security.filter.JwtAuthenticationFilter} (REST) and {@link
 * com.app.modules.comment.live.CommentWebSocketJwtHandshakeInterceptor} (WebSocket) so the two
 * paths cannot drift apart on what counts as a valid, authenticated caller.
 */
public interface TokenPrincipalResolver {

    /**
     * Resolves the given raw token to its authenticated principal.
     *
     * <p>Returns an empty {@link Optional} for every rejection reason without distinguishing which
     * one applied: a malformed, unparseable, wrong-signature, or expired token; a blacklisted
     * {@code jti}; a subject that does not resolve to a non-soft-deleted user; or an account whose
     * status is not {@code ACTIVE}.
     *
     * @param rawToken the raw JWT string, not the {@code Bearer } prefix
     * @return the resolved principal, or empty if the token does not authenticate
     */
    Optional<UserPrincipal> resolve(String rawToken);
}
