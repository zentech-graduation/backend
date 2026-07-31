package com.app.common.security.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.app.common.security.service.TokenPrincipalResolver;

/**
 * Periodically re-validates every locally-held WebSocket session's token and closes any session
 * whose token no longer authenticates.
 *
 * <p>The handshake check in {@link JwtHandshakeInterceptor} is point-in-time: a ban, suspension,
 * logout, or token blacklisting after a session is established has no effect on that session until
 * this sweep next runs. Re-using {@link TokenPrincipalResolver#resolve} means a WebSocket session
 * is subject to exactly the checks a REST request is subject to on every call, so the two paths
 * cannot drift apart on what counts as revoked.
 */
@Service
public class WebSocketRevocationSweepService {

    private static final Logger log =
            LoggerFactory.getLogger(WebSocketRevocationSweepService.class);

    private final WebSocketSessionRegistry registry;
    private final TokenPrincipalResolver tokenPrincipalResolver;

    public WebSocketRevocationSweepService(
            WebSocketSessionRegistry registry, TokenPrincipalResolver tokenPrincipalResolver) {
        this.registry = registry;
        this.tokenPrincipalResolver = tokenPrincipalResolver;
    }

    @Scheduled(fixedDelayString = "${app.websocket.revocation.interval:PT30S}")
    public void sweep() {
        for (WebSocketSessionRegistry.Entry entry : registry.snapshot()) {
            if (tokenPrincipalResolver.resolve(entry.token()).isEmpty()) {
                closeRevoked(entry.session());
            }
        }
    }

    private void closeRevoked(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException ex) {
            log.warn(
                    "Failed to close revoked WebSocket session {}: {}",
                    session.getId(),
                    ex.getMessage());
        }
    }
}
