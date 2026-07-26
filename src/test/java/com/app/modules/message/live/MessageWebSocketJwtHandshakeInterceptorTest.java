package com.app.modules.message.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.modules.message.repository.MessageUserRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class MessageWebSocketJwtHandshakeInterceptorTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private MessageUserRepository userRepository;

    private MessageWebSocketJwtHandshakeInterceptor interceptor;

    private static final String JTI = UUID.randomUUID().toString();
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        interceptor =
                new MessageWebSocketJwtHandshakeInterceptor(
                        jwtTokenProvider, tokenBlacklistService, userRepository);
    }

    @Test
    void handshake_validNonBlacklistedToken_returnsTrue() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, "user", JTI, Instant.now());
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNullAndStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.of(activeUser(userId)));

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attrs = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsKey(MessageWebSocketJwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
    }

    @Test
    void handshake_blacklistedToken_returnsFalse() throws Exception {
        JwtClaims claims = new JwtClaims(UUID.randomUUID(), "user", JTI, Instant.now());
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(true);

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshake_bannedOrDeletedAccount_returnsFalse() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, "user", JTI, Instant.now());
        when(jwtTokenProvider.validateAndParse(TOKEN)).thenReturn(claims);
        when(tokenBlacklistService.isBlacklisted(JTI)).thenReturn(false);
        // A previously-issued, still-unexpired, non-blacklisted token must not be enough on its
        // own once the account is no longer active or no longer exists.
        when(userRepository.findByIdAndDeletedAtIsNullAndStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ServerHttpRequest request = requestWithToken(TOKEN);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handshake_missingTokenParam_returnsFalse() throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/messages"));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    @Test
    void handshake_invalidToken_returnsFalse() throws Exception {
        when(jwtTokenProvider.validateAndParse(anyString()))
                .thenThrow(new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));

        ServerHttpRequest request = requestWithToken("bad-token");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
    }

    private static ServerHttpRequest requestWithToken(String token) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/messages?token=" + token));
        return request;
    }

    private static User activeUser(UUID id) {
        return User.builder().id(id).status(UserStatus.ACTIVE).build();
    }
}
