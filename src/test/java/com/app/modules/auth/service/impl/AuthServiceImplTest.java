package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtProperties;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.RefreshTokenService;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.util.IpExtractor;
import com.app.modules.auth.dto.request.ForgotPasswordRequest;
import com.app.modules.auth.dto.request.LoginRequest;
import com.app.modules.auth.dto.request.RefreshRequest;
import com.app.modules.auth.dto.request.RegisterRequest;
import com.app.modules.auth.dto.request.ResetPasswordRequest;
import com.app.modules.auth.dto.response.AuthResponse;
import com.app.modules.auth.dto.response.UserSummaryResponse;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.entity.UserSettings;
import com.app.modules.auth.enums.UserRole;
import com.app.modules.auth.enums.UserStatus;
import com.app.modules.auth.exception.TokenExpiredException;
import com.app.modules.auth.exception.TokenNotFoundException;
import com.app.modules.auth.mapper.AuthMapper;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.auth.repository.UserSettingsRepository;
import com.app.modules.auth.service.AuthForgotPasswordEventService;
import com.app.modules.auth.service.AuthMailEventService;
import com.app.modules.auth.service.TokenService;
import com.app.modules.auth.validation.UserStateValidator;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository credentialRepository;
    @Mock private UserSettingsRepository settingsRepository;
    @Mock private TokenService tokenService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthMailEventService authMailEventService;
    @Mock private AuthForgotPasswordEventService authForgotPasswordEventService;
    @Mock private ForgotPasswordTimingEqualizer forgotPasswordTimingEqualizer;
    @Mock private AuthMapper authMapper;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private IpExtractor ipExtractor;
    @Mock private UserStateValidator userStateValidator;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties =
                new JwtProperties("test-secret-32-chars-test-secret-", "iss", "App", 900, 3600);
        lenient().when(ipExtractor.extract(any(HttpServletRequest.class))).thenReturn("4.5.6.7");
        lenient()
                .when(authMapper.toUserSummaryResponse(any(User.class), anyBoolean()))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            boolean ev = inv.getArgument(1);
                            return new UserSummaryResponse(
                                    u.getId(),
                                    u.getUsername(),
                                    u.getEmail(),
                                    u.getDisplayName(),
                                    u.getRole() == null ? null : u.getRole().name(),
                                    ev);
                        });
        this.service =
                new AuthServiceImpl(
                        userRepository,
                        credentialRepository,
                        settingsRepository,
                        tokenService,
                        refreshTokenService,
                        jwtTokenProvider,
                        jwtProperties,
                        passwordEncoder,
                        authMailEventService,
                        authForgotPasswordEventService,
                        forgotPasswordTimingEqualizer,
                        authMapper,
                        tokenBlacklistService,
                        ipExtractor,
                        userStateValidator);
    }

    private MockHttpServletRequest stubRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("4.5.6.7");
        req.addHeader("User-Agent", "JUnit");
        return req;
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmailAndDeletedAtIsNull("a@b.c")).thenReturn(true);
        RegisterRequest req = new RegisterRequest("user1", "a@b.c", "password1", null);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.USER_EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedAtIsNull("user1")).thenReturn(true);
        RegisterRequest req = new RegisterRequest("user1", "a@b.c", "password1", null);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.USER_USERNAME_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_success_persistsUserCredentialAndSettings() {
        UUID newId = UUID.randomUUID();
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            u.setId(newId);
                            return u;
                        });
        when(passwordEncoder.encode("password1")).thenReturn("HASH");

        service.register(new RegisterRequest("user1", "a@b.c", "password1", null));

        verify(userRepository).save(any(User.class));
        verify(credentialRepository).save(any(UserCredential.class));
        verify(settingsRepository).save(any(UserSettings.class));
    }

    @Test
    void register_success_recordsMailEventsWithoutCreatingRawToken() {
        UUID newId = UUID.randomUUID();
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            u.setId(newId);
                            return u;
                        });
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");

        service.register(new RegisterRequest("user1", "a@b.c", "password1", null));

        verify(authMailEventService).publishUserRegistered(any(User.class));
        verify(authMailEventService).publishEmailVerificationRequested(any(User.class), eq(newId));
        verify(tokenService, never()).createEmailVerificationToken(any());
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmailAndDeletedAtIsNull("nobody@x.y"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.login(
                                        new LoginRequest("nobody@x.y", "password1"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User u = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(credential(u.getId(), "STORED-HASH")));
        when(passwordEncoder.matches(eq("wrong"), eq("STORED-HASH"))).thenReturn(false);

        assertThatThrownBy(
                        () -> service.login(new LoginRequest(u.getEmail(), "wrong"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void login_bannedUser_throwsAccountLocked() {
        User u = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(credential(u.getId(), "STORED-HASH")));
        doThrow(new AppException(ApiErrorCode.AUTH_ACCOUNT_LOCKED))
                .when(userStateValidator)
                .enforceActive(u);

        assertThatThrownBy(
                        () -> service.login(new LoginRequest(u.getEmail(), "any"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void login_suspendedUser_throwsAccountInactive() {
        User u = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(credential(u.getId(), "STORED-HASH")));
        doThrow(new AppException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE))
                .when(userStateValidator)
                .enforceActive(u);

        assertThatThrownBy(
                        () -> service.login(new LoginRequest(u.getEmail(), "any"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
    }

    @Test
    void login_nullPasswordHash_throwsInvalidCredentials() {
        User u = activeUser();
        UserCredential cred = credential(u.getId(), null);
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));

        assertThatThrownBy(
                        () -> service.login(new LoginRequest(u.getEmail(), "any"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void login_unverifiedEmail_throwsEmailNotVerified() {
        User u = activeUser();
        UserCredential cred = credential(u.getId(), "STORED-HASH");
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches(eq("password1"), eq("STORED-HASH"))).thenReturn(true);
        doThrow(new AppException(ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED))
                .when(userStateValidator)
                .enforceEmailVerified(cred);

        assertThatThrownBy(
                        () ->
                                service.login(
                                        new LoginRequest(u.getEmail(), "password1"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED);
    }

    @Test
    void login_success_returnsAccessAndRefreshTokens() {
        User u = activeUser();
        UserCredential cred = verifiedCredential(u.getId(), "STORED-HASH");
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches(eq("password1"), eq("STORED-HASH"))).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(eq(u.getId()), eq(u.getEmail()), eq("USER")))
                .thenReturn("ACCESS");
        when(refreshTokenService.issue(eq(u.getId()), any(), any(), any())).thenReturn("REFRESH");

        AuthResponse resp =
                service.login(new LoginRequest(u.getEmail(), "password1"), stubRequest());

        assertThat(resp.accessToken()).isEqualTo("ACCESS");
        assertThat(resp.refreshToken()).isEqualTo("REFRESH");
        assertThat(resp.user().id()).isEqualTo(u.getId());
    }

    @Test
    void refresh_bannedUser_revokesNewTokenAndThrowsAccountLocked() {
        UUID userId = UUID.randomUUID();
        String newRawToken = "NEW-TOKEN";
        when(refreshTokenService.rotate(eq("OLD"), anyString()))
                .thenReturn(new RefreshTokenService.RotationResult(newRawToken, userId));
        User u = activeUser();
        u.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));
        doThrow(new AppException(ApiErrorCode.AUTH_ACCOUNT_LOCKED))
                .when(userStateValidator)
                .enforceActive(u);

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("OLD"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);

        verify(refreshTokenService).revoke(newRawToken);
    }

    @Test
    void refresh_suspendedUser_revokesNewTokenAndThrowsAccountInactive() {
        UUID userId = UUID.randomUUID();
        String newRawToken = "NEW-TOKEN-SUSP";
        when(refreshTokenService.rotate(eq("OLD-SUSP"), anyString()))
                .thenReturn(new RefreshTokenService.RotationResult(newRawToken, userId));
        User u = activeUser();
        u.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));
        doThrow(new AppException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE))
                .when(userStateValidator)
                .enforceActive(u);

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("OLD-SUSP"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);

        verify(refreshTokenService).revoke(newRawToken);
    }

    @Test
    void refresh_validToken_returnsNewTokenPair() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.rotate(eq("OLD"), anyString()))
                .thenReturn(new RefreshTokenService.RotationResult("NEW", userId));
        User u = activeUser();
        u.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(userId))
                .thenReturn(Optional.of(credential(userId, "HASH")));
        when(jwtTokenProvider.generateAccessToken(eq(userId), eq(u.getEmail()), eq("USER")))
                .thenReturn("ACCESS-NEW");

        AuthResponse resp = service.refresh(new RefreshRequest("OLD"), stubRequest());

        assertThat(resp.accessToken()).isEqualTo("ACCESS-NEW");
        assertThat(resp.refreshToken()).isEqualTo("NEW");
    }

    @Test
    void refresh_invalidToken_propagatesAuthRefreshTokenInvalid() {
        when(refreshTokenService.rotate(anyString(), anyString()))
                .thenThrow(new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("BAD"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    @Test
    void logout_callsRevokeOnce() {
        service.logout(new RefreshRequest("RAW"));
        verify(refreshTokenService, times(1)).revoke("RAW");
    }

    @Test
    void logout_unknownToken_doesNotPropagateException() {
        // revoke is no-op-by-contract; the service must not propagate any exception even if
        // the underlying repository update returns zero rows.
        assertThatCode(() -> service.logout(new RefreshRequest("UNKNOWN")))
                .doesNotThrowAnyException();
    }

    @Test
    void logout_blacklistsAccessTokenAndRevokesRefreshToken() {
        String rawAccessToken = "raw-access";
        String jti = UUID.randomUUID().toString();
        Instant exp = Instant.now().plusSeconds(600);
        JwtClaims claims = new JwtClaims(UUID.randomUUID(), "x@y.z", "USER", jti, exp);
        when(jwtTokenProvider.validateAndParse(rawAccessToken)).thenReturn(claims);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("principal", rawAccessToken));
        try {
            service.logout(new RefreshRequest("REFRESH-RAW"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(tokenBlacklistService).blacklist(eq(jti), longThat(ttl -> ttl > 0L && ttl <= 600L));
        verify(refreshTokenService).revoke("REFRESH-RAW");
    }

    @Test
    void logout_invalidAccessToken_stillRevokesRefreshToken() {
        String rawAccessToken = "expired-access";
        when(jwtTokenProvider.validateAndParse(rawAccessToken))
                .thenThrow(new AppException(ApiErrorCode.AUTH_TOKEN_EXPIRED));

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("principal", rawAccessToken));
        try {
            service.logout(new RefreshRequest("REFRESH-RAW"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong());
        verify(refreshTokenService).revoke("REFRESH-RAW");
    }

    @Test
    void logout_noAuthContext_blacklistsNothingAndRevokesRefreshToken() {
        SecurityContextHolder.clearContext();

        service.logout(new RefreshRequest("REFRESH-RAW"));

        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong());
        verify(refreshTokenService).revoke("REFRESH-RAW");
    }

    @Test
    void resendVerification_unknownEmail_returnsSilentlyAndCreatesNoEvent() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@x.y"))
                .thenReturn(Optional.empty());

        service.resendVerification("ghost@x.y");

        verify(authMailEventService, never()).publishEmailVerificationRequested(any(), any());
        verify(tokenService, never()).createEmailVerificationToken(any());
    }

    @Test
    void resendVerification_verifiedAccount_returnsSilentlyAndCreatesNoEvent() {
        User u = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(verifiedCredential(u.getId(), "HASH")));

        service.resendVerification(u.getEmail());

        verify(authMailEventService, never()).publishEmailVerificationRequested(any(), any());
        verify(tokenService, never()).createEmailVerificationToken(any());
    }

    @Test
    void resendVerification_unverifiedAccount_recordsVerificationEventWithoutCreatingRawToken() {
        User u = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(credential(u.getId(), "HASH")));

        service.resendVerification(u.getEmail());

        verify(authMailEventService).publishEmailVerificationRequested(u, null);
        verify(tokenService, never()).createEmailVerificationToken(any());
    }

    @Test
    void forgotPassword_delegatesDurableEventRecordingAndEqualizesTiming() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("alice@example.com");

        service.forgotPassword(request);

        verify(authForgotPasswordEventService).recordForgotPasswordRequest(request.email());
        verify(forgotPasswordTimingEqualizer).equalizeFrom(anyLong());
        verify(tokenService, never()).createPasswordResetToken(any());
    }

    @Test
    void forgotPassword_equalizesTimingWhenEventRecordingFails() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("alice@example.com");
        doThrow(new IllegalStateException("db down"))
                .when(authForgotPasswordEventService)
                .recordForgotPasswordRequest(request.email());

        assertThatThrownBy(() -> service.forgotPassword(request))
                .isInstanceOf(IllegalStateException.class);

        verify(forgotPasswordTimingEqualizer).equalizeFrom(anyLong());
    }

    @Test
    void resetPassword_revokesAllSessionsAndRecordsNotificationEvent() {
        UUID userId = UUID.randomUUID();
        String raw = "RESET-RAW";
        when(tokenService.consumePasswordResetToken(raw)).thenReturn(userId);
        UserCredential cred = credential(userId, "OLD-HASH");
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(cred));
        when(passwordEncoder.encode("newPassword1")).thenReturn("NEW-HASH");
        User u = activeUser();
        u.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));

        service.resetPassword(new ResetPasswordRequest(raw, "newPassword1"));

        verify(refreshTokenService).revokeAllForUser(userId);
        verify(credentialRepository).save(cred);
        assertThat(cred.getPasswordHash()).isEqualTo("NEW-HASH");
        verify(authMailEventService).publishPasswordChanged(u);
    }

    @Test
    void resetPassword_unknownToken_throwsResetTokenInvalid() {
        when(tokenService.consumePasswordResetToken(anyString()))
                .thenThrow(new TokenNotFoundException("invalid"));

        assertThatThrownBy(
                        () ->
                                service.resetPassword(
                                        new ResetPasswordRequest("ghost", "newPassword1")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void resetPassword_expiredToken_throwsResetTokenInvalid() {
        when(tokenService.consumePasswordResetToken(anyString()))
                .thenThrow(new TokenExpiredException("expired"));

        assertThatThrownBy(
                        () ->
                                service.resetPassword(
                                        new ResetPasswordRequest("expired-token", "newPassword1")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    // FIX-23: resetPassword — credential with null passwordHash throws AUTH_RESET_TOKEN_INVALID
    @Test
    void resetPassword_oauthOnlyCredential_throwsResetTokenInvalid() {
        UUID userId = UUID.randomUUID();
        when(tokenService.consumePasswordResetToken("RESET-RAW")).thenReturn(userId);
        User u = activeUser();
        u.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));
        UserCredential cred = credential(userId, null);
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(cred));

        assertThatThrownBy(
                        () ->
                                service.resetPassword(
                                        new ResetPasswordRequest("RESET-RAW", "newPassword1")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    // FIX-7: verifyEmail — TokenNotFoundException converts to AUTH_VERIFY_TOKEN_INVALID
    @Test
    void verifyEmail_tokenNotFound_throwsVerifyTokenInvalid() {
        when(tokenService.consumeEmailVerificationToken("BAD-TOKEN"))
                .thenThrow(new TokenNotFoundException("not found"));

        assertThatThrownBy(() -> service.verifyEmail("BAD-TOKEN", stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID);
    }

    // FIX-7: verifyEmail — TokenExpiredException converts to AUTH_VERIFY_TOKEN_INVALID
    @Test
    void verifyEmail_tokenExpired_throwsVerifyTokenInvalid() {
        when(tokenService.consumeEmailVerificationToken("EXPIRED-TOKEN"))
                .thenThrow(new TokenExpiredException("expired"));

        assertThatThrownBy(() -> service.verifyEmail("EXPIRED-TOKEN", stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID);
    }

    // FIX-7: verifyEmail — valid token marks email verified and issues session
    @Test
    void verifyEmail_validToken_marksEmailVerifiedAndIssuesSession() {
        UUID userId = UUID.randomUUID();
        when(tokenService.consumeEmailVerificationToken("VALID-TOKEN")).thenReturn(userId);
        UserCredential cred = credential(userId, "HASH");
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(cred));
        User user = activeUser();
        user.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(eq(userId), anyString(), anyString()))
                .thenReturn("ACCESS");
        when(refreshTokenService.issue(eq(userId), any(), any(), any())).thenReturn("REFRESH");

        AuthResponse resp = service.verifyEmail("VALID-TOKEN", stubRequest());

        assertThat(resp.accessToken()).isEqualTo("ACCESS");
        assertThat(resp.refreshToken()).isEqualTo("REFRESH");
        assertThat(resp.user().emailVerified()).isTrue();
        verify(credentialRepository).save(cred);
        assertThat(cred.isEmailVerified()).isTrue();
        assertThat(cred.getEmailVerifiedAt()).isNotNull();
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

    private static UserCredential verifiedCredential(UUID userId, String hash) {
        return UserCredential.builder()
                .userId(userId)
                .passwordHash(hash)
                .emailVerified(true)
                .build();
    }

    @SuppressWarnings("unused")
    private HttpServletRequest unusedToSatisfyImport() {
        return null;
    }
}
