package com.app.modules.auth.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.service.AuthMailEventService;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthForgotPasswordEventServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository credentialRepository;
    @Mock private AuthMailEventService authMailEventService;

    @Test
    void recordForgotPasswordRequest_unknownEmailCreatesNoEventOrToken() {
        AuthForgotPasswordEventServiceImpl service = service();
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com"))
                .thenReturn(Optional.empty());

        service.recordForgotPasswordRequest("ghost@example.com");

        verify(authMailEventService, never()).publishPasswordResetRequested(any(), any());
        verify(authMailEventService, never()).publishOAuthAccountNoPassword(any(), any());
    }

    @Test
    void recordForgotPasswordRequest_inactiveUserCreatesNoEventOrToken() {
        AuthForgotPasswordEventServiceImpl service = service();
        User user = activeUser();
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));

        service.recordForgotPasswordRequest(user.getEmail());

        verify(authMailEventService, never()).publishPasswordResetRequested(any(), any());
        verify(authMailEventService, never()).publishOAuthAccountNoPassword(any(), any());
    }

    @Test
    void recordForgotPasswordRequest_localPasswordRecordsResetEventWithoutRawToken() {
        AuthForgotPasswordEventServiceImpl service = service();
        User user = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential(user.getId(), "HASH")));

        service.recordForgotPasswordRequest(user.getEmail());

        verify(authMailEventService).publishPasswordResetRequested(user, null);
        verify(authMailEventService, never()).publishOAuthAccountNoPassword(any(), any());
    }

    @Test
    void recordForgotPasswordRequest_oauthOnlyCredentialRecordsInformationalEvent() {
        AuthForgotPasswordEventServiceImpl service = service();
        User user = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential(user.getId(), null)));

        service.recordForgotPasswordRequest(user.getEmail());

        verify(authMailEventService).publishOAuthAccountNoPassword(user, null);
        verify(authMailEventService, never()).publishPasswordResetRequested(any(), any());
    }

    @Test
    void recordForgotPasswordRequest_missingCredentialRecordsInformationalEvent() {
        AuthForgotPasswordEventServiceImpl service = service();
        User user = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        service.recordForgotPasswordRequest(user.getEmail());

        verify(authMailEventService).publishOAuthAccountNoPassword(eq(user), eq(null));
        verify(authMailEventService, never()).publishPasswordResetRequested(any(), any());
    }

    private AuthForgotPasswordEventServiceImpl service() {
        return new AuthForgotPasswordEventServiceImpl(
                userRepository, credentialRepository, authMailEventService);
    }

    private static User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .displayName("Alice")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .isPrivate(false)
                .isVerified(false)
                .build();
    }

    private static UserCredential credential(UUID userId, String hash) {
        return UserCredential.builder()
                .userId(userId)
                .passwordHash(hash)
                .emailVerified(false)
                .build();
    }
}
