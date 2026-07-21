package com.app.modules.auth.service.impl;

import static org.mockito.ArgumentMatchers.any;
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
class AuthResendVerificationEventServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository credentialRepository;
    @Mock private AuthMailEventService authMailEventService;

    @Test
    void recordResendVerificationRequest_unknownEmailCreatesNoEvent() {
        AuthResendVerificationEventServiceImpl service = service();
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com"))
                .thenReturn(Optional.empty());

        service.recordResendVerificationRequest("ghost@example.com");

        verify(authMailEventService, never()).publishEmailVerificationRequested(any(), any());
    }

    @Test
    void recordResendVerificationRequest_verifiedAccountCreatesNoEvent() {
        AuthResendVerificationEventServiceImpl service = service();
        User user = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential(user.getId(), true)));

        service.recordResendVerificationRequest(user.getEmail());

        verify(authMailEventService, never()).publishEmailVerificationRequested(any(), any());
    }

    @Test
    void recordResendVerificationRequest_unverifiedAccountRecordsVerificationEvent() {
        AuthResendVerificationEventServiceImpl service = service();
        User user = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential(user.getId(), false)));

        service.recordResendVerificationRequest(user.getEmail());

        verify(authMailEventService).publishEmailVerificationRequested(user, null);
    }

    private AuthResendVerificationEventServiceImpl service() {
        return new AuthResendVerificationEventServiceImpl(
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

    private static UserCredential credential(UUID userId, boolean emailVerified) {
        return UserCredential.builder()
                .userId(userId)
                .passwordHash("HASH")
                .emailVerified(emailVerified)
                .build();
    }
}
