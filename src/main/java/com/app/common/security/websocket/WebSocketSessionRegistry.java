package com.app.common.security.websocket;

import java.util.List;
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
 */
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, Entry> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, String token) {
        sessions.put(session.getId(), new Entry(session, token));
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Point-in-time snapshot of every tracked session, safe to iterate while the map mutates. */
    public List<Entry> snapshot() {
        return List.copyOf(sessions.values());
    }

    public record Entry(WebSocketSession session, String token) {}
}
