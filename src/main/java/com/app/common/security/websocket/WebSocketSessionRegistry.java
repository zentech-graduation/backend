package com.app.common.security.websocket;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Tracks every locally-held WebSocket session alongside the raw access token its handshake was
 * authenticated with, so {@link WebSocketRevocationSweepService} can periodically re-validate each
 * session and close ones whose token has since been revoked.
 *
 * <p>Registration is instance-local: each application instance tracks only the sessions it holds,
 * which is what makes the periodic sweep correct without any cross-instance coordination.
 *
 * <p>Also the session-to-viewer lookup outbound broadcast filters use (see the comment module's
 * live fan-out) to resolve which authenticated user a given outbound STOMP frame's target session
 * belongs to, without a second parallel registry.
 */
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, Entry> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, String token, UUID viewerId) {
        sessions.put(session.getId(), new Entry(session, token, viewerId));
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Point-in-time snapshot of every tracked session, safe to iterate while the map mutates. */
    public List<Entry> snapshot() {
        return List.copyOf(sessions.values());
    }

    /**
     * Resolves the authenticated viewer a locally-held session belongs to.
     *
     * @param sessionId WebSocket session id
     * @return the session's viewer id, or null when the session is not tracked (already closed) or
     *     carried no resolvable principal at registration
     */
    public UUID findViewerId(String sessionId) {
        Entry entry = sessions.get(sessionId);
        return entry == null ? null : entry.viewerId();
    }

    public record Entry(WebSocketSession session, String token, UUID viewerId) {}
}
