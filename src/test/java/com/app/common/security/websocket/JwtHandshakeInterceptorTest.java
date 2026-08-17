package com.app.common.security.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;
import com.app.common.security.util.IpExtractor;
import com.app.modules.auth.service.WebSocketTicketService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock private TokenPrincipalResolver tokenPrincipalResolver;
    @Mock private WebSocketTicketService webSocketTicketService;
    @Mock private IpExtractor ipExtractor;

    private JwtHandshakeInterceptor interceptor;
    private ListAppender<ILoggingEvent> logAppender;

    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        interceptor =
                new JwtHandshakeInterceptor(
                        tokenPrincipalResolver, webSocketTicketService, ipExtractor);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(JwtHandshakeInterceptor.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(JwtHandshakeInterceptor.class))
                .detachAppender(logAppender);
    }

    @Test
    void handshake_validTicket_redeemsItAndStoresTheRecoveredToken() throws Exception {
        // The ticket is redeemed server-side and the raw token is what lands in the attributes:
        // WebSocketRevocationSweepService re-resolves that value to decide whether a live session
        // is still authenticated, so storing the ticket instead would silently break revocation.
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), null, "USER", "ACTIVE");
        when(webSocketTicketService.consumeTicket("good-ticket")).thenReturn(TOKEN);
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.of(principal));

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result =
                interceptor.beforeHandshake(
                        requestWithTicket("good-ticket"), response, null, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry(JwtHandshakeInterceptor.TOKEN_ATTRIBUTE, TOKEN);
        assertThat(attrs).containsEntry(JwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal);
    }

    @Test
    void handshake_redeemedOrUnknownTicket_returnsFalse() throws Exception {
        when(webSocketTicketService.consumeTicket("used"))
                .thenThrow(new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));

        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result =
                interceptor.beforeHandshake(
                        requestWithTicket("used"), response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshake_resolvedPrincipal_returnsTrue() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), null, "USER", "ACTIVE");
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.of(principal));

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry(JwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE, principal);
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void handshake_blacklistedToken_returnsFalse() throws Exception {
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshake_missingTokenParam_returnsFalse() throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments"));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshake_invalidToken_returnsFalse() throws Exception {
        when(tokenPrincipalResolver.resolve("bad-token")).thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken("bad-token");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    @Test
    void handshake_nonActiveAccountValidToken_returnsFalse() throws Exception {
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    @Test
    void handshake_missingToken_logsWarnWithEndpointAndRemoteAddress() {
        MockHttpServletRequest servletRequest =
                new MockHttpServletRequest("GET", "/ws/notifications");
        servletRequest.setRemoteAddr("203.0.113.7");
        when(ipExtractor.extract(any())).thenReturn("203.0.113.7");
        ServerHttpRequest request =
                new ServletServerHttpRequest(servletRequest) {
                    @Override
                    public URI getURI() {
                        return URI.create("ws://localhost/ws/notifications");
                    }
                };
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        List<ILoggingEvent> events = logAppender.list;
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("endpoint=/ws/notifications")
                .contains("reason=missing_credential")
                .contains("remoteAddress=203.0.113.7");
    }

    @Test
    void handshake_resolverRejects_logsWarnWithEndpointAndRemoteAddress() {
        when(tokenPrincipalResolver.resolve(TOKEN)).thenReturn(Optional.empty());
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/comments");
        servletRequest.setRemoteAddr("198.51.100.9");
        when(ipExtractor.extract(any())).thenReturn("198.51.100.9");
        ServerHttpRequest request =
                new ServletServerHttpRequest(servletRequest) {
                    @Override
                    public URI getURI() {
                        return URI.create("ws://localhost/ws/comments?token=" + TOKEN);
                    }
                };
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        List<ILoggingEvent> events = logAppender.list;
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("endpoint=/ws/comments")
                .contains("reason=rejected")
                .contains("remoteAddress=198.51.100.9");
        assertThat(event.getFormattedMessage()).doesNotContain(TOKEN);
    }

    @Test
    void handshake_missingToken_nonServletRequest_logsRemoteAddressFromSocketAddress()
            throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments"));
        when(request.getRemoteAddress())
                .thenReturn(new InetSocketAddress(InetAddress.getByName("192.0.2.10"), 5000));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        List<ILoggingEvent> events = logAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).contains("remoteAddress=192.0.2.10");
    }

    @Test
    void handshake_missingToken_nonServletRequestWithNullSocketAddress_logsUnknown() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments"));
        when(request.getRemoteAddress()).thenReturn(null);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        List<ILoggingEvent> events = logAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).contains("remoteAddress=unknown");
    }

    private static ServerHttpRequest requestWithTicket(String ticket) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI())
                .thenReturn(URI.create("ws://localhost/ws/comments?ticket=" + ticket));
        return request;
    }

    private static ServerHttpRequest requestWithToken(String token) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/comments?token=" + token));
        return request;
    }
}
