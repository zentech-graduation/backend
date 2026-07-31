package com.app.common.security.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class WebSocketRevocationSweepServiceTest {

    @Mock private WebSocketSessionRegistry registry;
    @Mock private TokenPrincipalResolver tokenPrincipalResolver;
    @Mock private WebSocketSession validSession;
    @Mock private WebSocketSession revokedSession;

    private WebSocketRevocationSweepService sweepService;

    @BeforeEach
    void setUp() {
        sweepService = new WebSocketRevocationSweepService(registry, tokenPrincipalResolver);
    }

    @Test
    void sweep_closesOnlyTheSessionWhoseTokenNoLongerResolves() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), null, "USER", "ACTIVE");
        when(registry.snapshot())
                .thenReturn(
                        List.of(
                                new WebSocketSessionRegistry.Entry(validSession, "valid-token"),
                                new WebSocketSessionRegistry.Entry(
                                        revokedSession, "revoked-token")));
        when(tokenPrincipalResolver.resolve("valid-token")).thenReturn(Optional.of(principal));
        when(tokenPrincipalResolver.resolve("revoked-token")).thenReturn(Optional.empty());
        when(revokedSession.isOpen()).thenReturn(true);

        sweepService.sweep();

        verify(validSession, never()).close(any());
        verify(revokedSession).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void sweep_skipsAlreadyClosedRevokedSession() throws Exception {
        when(registry.snapshot())
                .thenReturn(
                        List.of(
                                new WebSocketSessionRegistry.Entry(
                                        revokedSession, "revoked-token")));
        when(tokenPrincipalResolver.resolve("revoked-token")).thenReturn(Optional.empty());
        when(revokedSession.isOpen()).thenReturn(false);

        sweepService.sweep();

        verify(revokedSession, never()).close(any());
    }

    @Test
    void sweep_emptyRegistry_doesNothing() {
        when(registry.snapshot()).thenReturn(List.of());

        sweepService.sweep();

        verify(tokenPrincipalResolver, never()).resolve(any());
    }
}
