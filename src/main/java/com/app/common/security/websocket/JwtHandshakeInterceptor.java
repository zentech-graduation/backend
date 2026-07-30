package com.app.common.security.websocket;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;
import com.app.common.security.util.IpExtractor;

/**
 * Authenticates a WebSocket upgrade using a JWT supplied as the {@code token} query parameter,
 * delegating the same signature/expiry/blacklist/account-status checks the REST path enforces to
 * {@link TokenPrincipalResolver}.
 *
 * <p>Browsers cannot set Authorization headers on a WebSocket upgrade, so the access token rides as
 * a query parameter. On success the resolved {@link UserPrincipal} is stored in the handshake
 * attributes under {@code principal} for later per-subscription authorization. Shared by every
 * STOMP endpoint in the application so the WebSocket path cannot drift from the REST path on what
 * counts as authenticated.
 *
 * <p>Every rejection is logged at WARN with the endpoint, a coarse reason, and the caller's remote
 * address, since this is a {@code permitAll()} path at the Spring Security layer and this
 * interceptor is the sole authentication gate. The reason is deliberately coarse: {@code
 * missing_token} when no token was supplied at all, {@code rejected} for every case {@link
 * TokenPrincipalResolver} declines, since that resolver intentionally does not distinguish expired,
 * bad-signature, blacklisted, or non-{@code ACTIVE} outcomes from one another.
 *
 * <p>The raw token is also stored under {@code token} so a session-tracking decorator can register
 * it for periodic revocation re-checks; the handshake check is otherwise point-in-time and a
 * connection established just before a ban, suspension, or logout would otherwise survive until the
 * token's natural expiry.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE = "principal";
    public static final String TOKEN_ATTRIBUTE = "token";

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final TokenPrincipalResolver tokenPrincipalResolver;
    private final IpExtractor ipExtractor;

    public JwtHandshakeInterceptor(
            TokenPrincipalResolver tokenPrincipalResolver, IpExtractor ipExtractor) {
        this.tokenPrincipalResolver = tokenPrincipalResolver;
        this.ipExtractor = ipExtractor;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null || token.isBlank()) {
            logRejection(request, "missing_token");
            return false;
        }
        Optional<UserPrincipal> principal = tokenPrincipalResolver.resolve(token);
        if (principal.isEmpty()) {
            logRejection(request, "rejected");
            return false;
        }
        attributes.put(PRINCIPAL_ATTRIBUTE, principal.get());
        attributes.put(TOKEN_ATTRIBUTE, token);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No post-handshake action required.
    }

    private static String extractToken(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }

    private void logRejection(ServerHttpRequest request, String reason) {
        log.warn(
                "WebSocket handshake rejected: endpoint={} reason={} remoteAddress={}",
                request.getURI().getPath(),
                reason,
                remoteAddress(request));
    }

    private String remoteAddress(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return ipExtractor.extract(servletRequest.getServletRequest());
        }
        return request.getRemoteAddress() == null
                ? "unknown"
                : request.getRemoteAddress().getAddress().getHostAddress();
    }
}
