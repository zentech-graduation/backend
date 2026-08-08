package com.app.common.security.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import com.app.common.security.user.UserPrincipal;

/**
 * Registers each established session with {@link WebSocketSessionRegistry} on connect and removes
 * it on close, so the registry always reflects exactly the sessions this instance currently holds.
 *
 * <p>Handshake attributes (populated by {@link JwtHandshakeInterceptor}) are copied onto the
 * resulting {@link WebSocketSession} by the framework, so the raw token and resolved principal are
 * read directly from {@code session.getAttributes()} with no extra plumbing.
 */
@Component
public class SessionTrackingWebSocketHandlerDecoratorFactory
        implements WebSocketHandlerDecoratorFactory {

    private final WebSocketSessionRegistry registry;

    public SessionTrackingWebSocketHandlerDecoratorFactory(WebSocketSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                Object token = session.getAttributes().get(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE);
                if (token instanceof String rawToken) {
                    Object principal =
                            session.getAttributes()
                                    .get(JwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
                    registry.register(
                            session,
                            rawToken,
                            principal instanceof UserPrincipal userPrincipal
                                    ? userPrincipal.userId()
                                    : null);
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
                    throws Exception {
                registry.unregister(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }
}
