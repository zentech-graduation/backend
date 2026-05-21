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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.app.common.config.app.AppProperties;
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
import com.app.modules.auth.service.TokenService;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.service.MailService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository credentialRepository;
    @Mock private UserSettingsRepository settingsRepository;
    @Mock private TokenService tokenService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailService mailService;
    @Mock private MailProperties mailProperties;
    @Mock private AuthMapper authMapper;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private IpExtractor ipExtractor;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties =
                new JwtProperties("test-secret-32-chars-test-secret-", "iss", "App", 900, 3600);
        AppProperties appProperties = new AppProperties("https://app.local");
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
                        mailService,
                        mailProperties,
                        appProperties,
                        authMapper,
                        tokenBlacklistService,
                        ipExtractor);
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

        assertThatThrownBy(() -> service.register(req, stubRequest()))
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

        assertThatThrownBy(() -> service.register(req, stubRequest()))
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
        when(tokenService.createEmailVerificationToken(newId)).thenReturn("verify-token");

        service.register(new RegisterRequest("user1", "a@b.c", "password1", null), stubRequest());

        verify(userRepository).save(any(User.class));
        verify(credentialRepository).save(any(UserCredential.class));
        verify(settingsRepository).save(any(UserSettings.class));
        verify(jwtTokenProvider, never()).generateAccessToken(any(), anyString(), anyString());
        verify(refreshTokenService, never()).issue(any(), any(), any(), any());
    }

    @Test
    void register_success_dispatchesVerificationAndWelcomeMail() {
        UUID newId = UUID.randomUUID();
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            u.setId(newId);
                            return u;
                        });
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(tokenService.createEmailVerificationToken(newId)).thenReturn("vf");

        service.register(new RegisterRequest("user1", "a@b.c", "password1", null), stubRequest());

        verify(mailService, times(1)).sendEmailVerification(eq("a@b.c"), eq("user1"), anyString());
        verify(mailService, times(1)).sendWelcome(eq("a@b.c"), eq("user1"));
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
        u.setStatus(UserStatus.BANNED);
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(credential(u.getId(), "STORED-HASH")));

        assertThatThrownBy(
                        () -> service.login(new LoginRequest(u.getEmail(), "any"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void login_suspendedUser_throwsAccountInactive() {
        User u = activeUser();
        u.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId()))
                .thenReturn(Optional.of(credential(u.getId(), "STORED-HASH")));

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
    void login_unverifiedEmail_throwsAccountInactive() {
        User u = activeUser();
        UserCredential cred = credential(u.getId(), "STORED-HASH");
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));
        when(passwordEncoder.matches(eq("password1"), eq("STORED-HASH"))).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.login(
                                        new LoginRequest(u.getEmail(), "password1"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
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
        u.setStatus(UserStatus.BANNED);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("OLD"), stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);

        // Proves the revocation was requested. In a mock-based test we cannot assert
        // revoked_at IS NOT NULL on the DB row directly — that invariant is covered by
        // AuthControllerIT.refresh_bannedUser_returns403AndOldTokenIsDurablyRevoked().
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
        u.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));

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
    void forgotPassword_unknownEmail_returnsSilentlyAndSendsNoMail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@x.y"))
                .thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest("ghost@x.y"));

        verify(tokenService, never()).createPasswordResetToken(any());
        verify(mailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPassword_knownEmail_dispatchesResetMail() {
        User u = activeUser();
        UserCredential cred = credential(u.getId(), "HASH");
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));
        when(tokenService.createPasswordResetToken(u.getId())).thenReturn("RESET-RAW");
        when(mailProperties.getFrontendBaseUrl()).thenReturn("http://localhost:5173");
        when(mailProperties.getResetPasswordPath()).thenReturn("/reset-password");

        service.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService)
                .sendPasswordReset(eq(u.getEmail()), eq(u.getDisplayName()), urlCaptor.capture());
        assertThat(urlCaptor.getValue())
                .startsWith("http://localhost:5173/reset-password?token=")
                .contains("RESET-RAW");
    }

    @Test
    void resetPassword_revokesAllSessionsAndDispatchesNotification() {
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
        verify(mailService).sendPasswordChanged(u.getEmail(), u.getDisplayName());
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

    // FIX-6: forgotPassword async timing — unknown email path
    @Test
    void forgotPassword_unknownEmail_doesNotCreateTokenOrSendMail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@x.y"))
                .thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest("ghost@x.y"));

        verify(tokenService, never()).createPasswordResetToken(any());
        verify(mailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
        verify(mailService, never()).sendOAuthAccountNoPassword(anyString(), anyString());
    }

    // FIX-6: forgotPassword async timing — non-ACTIVE user path
    @Test
    void forgotPassword_inactiveUser_doesNotCreateTokenOrSendResetMail() {
        User u = activeUser();
        u.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));

        service.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        verify(tokenService, never()).createPasswordResetToken(any());
        verify(mailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // FIX-6 + FIX-23: forgotPassword — ACTIVE user with local password sends reset mail
    @Test
    void forgotPassword_activeUserWithPassword_sendsResetMail() {
        User u = activeUser();
        UserCredential cred = credential(u.getId(), "HASH");
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));
        when(tokenService.createPasswordResetToken(u.getId())).thenReturn("RESET-RAW");
        when(mailProperties.getFrontendBaseUrl()).thenReturn("http://localhost:5173");
        when(mailProperties.getResetPasswordPath()).thenReturn("/reset-password");

        service.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        verify(tokenService).createPasswordResetToken(u.getId());
        verify(mailService)
                .sendPasswordReset(eq(u.getEmail()), eq(u.getDisplayName()), anyString());
        verify(mailService, never()).sendOAuthAccountNoPassword(anyString(), anyString());
    }

    // FIX-23: forgotPassword — OAuth-only user (null passwordHash) sends informational mail
    @Test
    void forgotPassword_oauthOnlyUser_sendsOAuthMailAndDoesNotCreateToken() {
        User u = activeUser();
        UserCredential cred = credential(u.getId(), null);
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.of(cred));

        service.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        verify(mailService).sendOAuthAccountNoPassword(u.getEmail(), u.getDisplayName());
        verify(tokenService, never()).createPasswordResetToken(any());
        verify(mailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // FIX-23: forgotPassword — OAuth-only user with no credential row sends informational mail
    @Test
    void forgotPassword_oauthOnlyUserNoCred_sendsOAuthMailAndDoesNotCreateToken() {
        User u = activeUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(u.getEmail())).thenReturn(Optional.of(u));
        when(credentialRepository.findByUserId(u.getId())).thenReturn(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        verify(mailService).sendOAuthAccountNoPassword(u.getEmail(), u.getDisplayName());
        verify(tokenService, never()).createPasswordResetToken(any());
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

    // FIX-7: verifyEmail — valid token completes successfully
    @Test
    void verifyEmail_validToken_marksEmailVerifiedAndIssuesSession() {
        UUID userId = UUID.randomUUID();
        when(tokenService.consumeEmailVerificationToken("VALID-TOKEN")).thenReturn(userId);
        UserCredential cred = credential(userId, "HASH");
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(cred));
        User u = activeUser();
        u.setId(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));
        when(jwtTokenProvider.generateAccessToken(eq(userId), eq(u.getEmail()), eq("USER")))
                .thenReturn("ACCESS");
        when(refreshTokenService.issue(eq(userId), any(), any(), any())).thenReturn("REFRESH");

        AuthResponse resp = service.verifyEmail("VALID-TOKEN", stubRequest());

        verify(credentialRepository).save(cred);
        assertThat(cred.isEmailVerified()).isTrue();
        assertThat(cred.getEmailVerifiedAt()).isNotNull();
        assertThat(resp.accessToken()).isEqualTo("ACCESS");
        assertThat(resp.refreshToken()).isEqualTo("REFRESH");
        assertThat(resp.user().id()).isEqualTo(userId);
        assertThat(resp.user().emailVerified()).isTrue();
    }

    @Test
    void verifyEmail_bannedUser_marksVerifiedThenThrowsAccountLocked() {
        UUID userId = UUID.randomUUID();
        when(tokenService.consumeEmailVerificationToken("VALID-TOKEN")).thenReturn(userId);
        UserCredential cred = credential(userId, "HASH");
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(cred));
        User u = activeUser();
        u.setId(userId);
        u.setStatus(UserStatus.BANNED);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.verifyEmail("VALID-TOKEN", stubRequest()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);

        // Credential is saved (emailVerified=true) before the status check rejects the session
        verify(credentialRepository).save(cred);
        assertThat(cred.isEmailVerified()).isTrue();
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
