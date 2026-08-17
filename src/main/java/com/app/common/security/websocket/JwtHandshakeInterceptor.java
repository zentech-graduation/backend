package com.app.common.security.websocket;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.common.exception.AppException;
import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;
import com.app.common.security.util.IpExtractor;
import com.app.modules.auth.service.WebSocketTicketService;

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
    private final WebSocketTicketService webSocketTicketService;
    private final IpExtractor ipExtractor;

    public JwtHandshakeInterceptor(
            TokenPrincipalResolver tokenPrincipalResolver,
            WebSocketTicketService webSocketTicketService,
            IpExtractor ipExtractor) {
        this.tokenPrincipalResolver = tokenPrincipalResolver;
        this.webSocketTicketService = webSocketTicketService;
        this.ipExtractor = ipExtractor;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = resolveCredential(request);
        if (token == null || token.isBlank()) {
            logRejection(request, "missing_credential");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Optional<UserPrincipal> principal = tokenPrincipalResolver.resolve(token);
        if (principal.isEmpty()) {
            logRejection(request, "rejected");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
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

    /**
     * Resolves the handshake credential to a raw access token.
     *
     * <p>A {@code ticket} is redeemed server-side and preferred, because it keeps the access token
     * out of the URL and therefore out of every proxy and CDN access log. A {@code token} parameter
     * is still accepted for now so a client that has not adopted tickets keeps working; that path
     * is scheduled for removal once no client uses it.
     *
     * <p>Either way the value returned is the raw access token, which the caller stores under
     * {@link #TOKEN_ATTRIBUTE}. The revocation sweep re-resolves that value, so it must be the
     * token and never the ticket.
     */
    private String resolveCredential(ServerHttpRequest request) {
        MultiValueMap<String, String> params =
                UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();

        String ticket = params.getFirst("ticket");
        if (ticket != null && !ticket.isBlank()) {
            try {
                return webSocketTicketService.consumeTicket(ticket);
            } catch (AppException ex) {
                return null;
            }
        }
        return params.getFirst("token");
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
        return Optional.ofNullable(request.getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown");
    }
}
