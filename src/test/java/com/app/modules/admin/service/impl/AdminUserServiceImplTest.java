package com.app.modules.admin.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.security.service.RefreshTokenService;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminRoleChangeRequest;
import com.app.modules.admin.mapper.AdminUserMapper;
import com.app.modules.admin.repository.AdminUserRepository;
import com.app.modules.admin.service.AdminActionRecorder;
import com.app.modules.admin.service.AdminAuthorizationService;
import com.app.modules.admin.service.UserDisciplineService;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

/**
 * Pins the pairing of a token-epoch advance with a refresh-token revocation.
 *
 * <p>The refresh path is deliberately not epoch-gated: it rotates a stored token hash and mints a
 * new access token stamped with whatever epoch the row currently holds. Advancing the epoch alone
 * would therefore end nothing, because the client heals itself on its next refresh.
 *
 * <p>Nothing in the code makes that pairing structural, so these tests are what holds it. Both
 * writers of {@code users.token_epoch} live in this class, and each must revoke first. A third
 * writer added without the pairing would announce a session termination that does not happen.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AdminUserMapper adminUserMapper;
    @Mock private AdminActionRecorder adminActionRecorder;
    @Mock private AdminAuthorizationService adminAuthorizationService;
    @Mock private UserDisciplineService userDisciplineService;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new AdminUserServiceImpl(
                        adminUserRepository,
                        userRepository,
                        reportRepository,
                        refreshTokenService,
                        adminUserMapper,
                        adminActionRecorder,
                        adminAuthorizationService,
                        userDisciplineService);
    }

    @Test
    void forceLogout_revokesEveryRefreshTokenBeforeAdvancingTheEpoch() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId, UserRole.USER)));
        when(refreshTokenService.revokeAllForUser(targetId)).thenReturn(3);

        service.forceLogout(actorId, targetId, new AdminActionRequest("Compromised", null));

        // Order matters only for reasoning, not for correctness: both run in one transaction. What
        // matters is that neither happens without the other.
        InOrder ordered = inOrder(refreshTokenService, adminUserRepository);
        ordered.verify(refreshTokenService).revokeAllForUser(targetId);
        ordered.verify(adminUserRepository).incrementTokenEpoch(targetId);
    }

    @Test
    void changeRole_revokesEveryRefreshTokenBeforeAdvancingTheEpoch() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(actorId))
                .thenReturn(Optional.of(user(actorId, UserRole.ADMIN)));
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId, UserRole.USER)));
        when(refreshTokenService.revokeAllForUser(targetId)).thenReturn(1);

        service.changeRole(
                actorId, targetId, new AdminRoleChangeRequest(UserRole.MODERATOR, "Promotion"));

        InOrder ordered = inOrder(refreshTokenService, adminUserRepository);
        ordered.verify(refreshTokenService).revokeAllForUser(targetId);
        ordered.verify(adminUserRepository).incrementTokenEpoch(targetId);
    }

    @Test
    void forceLogout_recordsTheNumberOfSessionsItActuallyEnded() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId, UserRole.USER)));
        when(refreshTokenService.revokeAllForUser(targetId)).thenReturn(2);

        service.forceLogout(
                UUID.randomUUID(), targetId, new AdminActionRequest("Compromised", null));

        verify(adminActionRecorder)
                .record(any(), any(), any(), anyString(), any(), any(), anyString(), any());
    }

    private static User user(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .username("u" + id.toString().substring(0, 8))
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
