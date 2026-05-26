package com.app.modules.auth.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
import com.app.modules.auth.dto.request.OAuth2ExchangeRequest;
import com.app.modules.auth.dto.request.RefreshRequest;
import com.app.modules.auth.dto.request.RegisterRequest;
import com.app.modules.auth.dto.request.ResetPasswordRequest;
import com.app.modules.auth.dto.response.AuthResponse;
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
import com.app.modules.auth.service.AuthService;
import com.app.modules.auth.service.OAuth2ExchangeCodeService;
import com.app.modules.auth.service.TokenService;
import com.app.modules.auth.validation.UserStateValidator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final UserSettingsRepository settingsRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailEventService authMailEventService;
    private final AuthForgotPasswordEventService authForgotPasswordEventService;
    private final ForgotPasswordTimingEqualizer forgotPasswordTimingEqualizer;
    private final AuthMapper authMapper;
    private final TokenBlacklistService tokenBlacklistService;
    private final IpExtractor ipExtractor;
    private final UserStateValidator userStateValidator;
    private final OAuth2ExchangeCodeService oauth2ExchangeCodeService;

    // Pre-computed BCrypt hash used to equalize CPU work on login failure paths so that
    // "email not found" is indistinguishable from "wrong password" via response timing.
    private String dummyPasswordHash;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            UserSettingsRepository settingsRepository,
            TokenService tokenService,
            RefreshTokenService refreshTokenService,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder,
            AuthMailEventService authMailEventService,
            AuthForgotPasswordEventService authForgotPasswordEventService,
            ForgotPasswordTimingEqualizer forgotPasswordTimingEqualizer,
            AuthMapper authMapper,
            TokenBlacklistService tokenBlacklistService,
            IpExtractor ipExtractor,
            UserStateValidator userStateValidator,
            OAuth2ExchangeCodeService oauth2ExchangeCodeService) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.settingsRepository = settingsRepository;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.authMailEventService = authMailEventService;
        this.authForgotPasswordEventService = authForgotPasswordEventService;
        this.forgotPasswordTimingEqualizer = forgotPasswordTimingEqualizer;
        this.authMapper = authMapper;
        this.tokenBlacklistService = tokenBlacklistService;
        this.ipExtractor = ipExtractor;
        this.userStateValidator = userStateValidator;
        this.oauth2ExchangeCodeService = oauth2ExchangeCodeService;
    }

    @PostConstruct
    void initDummyPasswordHash() {
        // Encoded once at bean init; the value is irrelevant because this hash never matches
        // any real user password. It exists solely to keep BCrypt cost on the failure paths.
        this.dummyPasswordHash =
                passwordEncoder.encode("login-timing-equalizer-" + UUID.randomUUID());
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new AppException(ApiErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
            throw new AppException(ApiErrorCode.USER_USERNAME_ALREADY_EXISTS);
        }

        String displayName =
                StringUtils.hasText(request.displayName())
                        ? request.displayName()
                        : request.username();

        User user =
                User.builder()
                        .username(request.username())
                        .email(request.email())
                        .displayName(displayName)
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .isPrivate(false)
                        .isVerified(false)
                        .build();
        user = userRepository.save(user);

        UserCredential credential =
                UserCredential.builder()
                        .userId(user.getId())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .emailVerified(false)
                        .build();
        credentialRepository.save(credential);

        settingsRepository.save(UserSettings.builder().userId(user.getId()).build());

        authMailEventService.publishUserRegistered(user);
        authMailEventService.publishEmailVerificationRequested(user, user.getId());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email()).orElse(null);
        UserCredential credential =
                user == null ? null : credentialRepository.findByUserId(user.getId()).orElse(null);

        // Run BCrypt unconditionally so unknown-email, missing-credential, and wrong-password
        // paths are indistinguishable via response timing. The dummy hash is a real BCrypt
        // hash that no user password can satisfy.
        String hashForCompare =
                credential != null && credential.getPasswordHash() != null
                        ? credential.getPasswordHash()
                        : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashForCompare);

        if (user == null || credential == null || credential.getPasswordHash() == null) {
            throw new AppException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        userStateValidator.enforceActive(user);

        if (!passwordMatches) {
            throw new AppException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        userStateValidator.enforceEmailVerified(credential);

        return issueSession(user, credential.isEmailVerified(), httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        RefreshTokenService.RotationResult rotation =
                refreshTokenService.rotate(
                        request.refreshToken(), ipExtractor.extract(httpRequest));

        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(rotation.userId())
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID));

        try {
            userStateValidator.enforceActive(user);
        } catch (AppException ex) {
            refreshTokenService.revoke(rotation.newRawToken());
            throw ex;
        }

        boolean emailVerified =
                credentialRepository
                        .findByUserId(user.getId())
                        .map(UserCredential::isEmailVerified)
                        .orElse(false);

        String accessToken =
                jwtTokenProvider.generateAccessToken(
                        user.getId(), user.getEmail(), user.getRole().name());

        return new AuthResponse(
                accessToken,
                rotation.newRawToken(),
                jwtProperties.accessTokenTtl(),
                AuthResponse.BEARER,
                authMapper.toUserSummaryResponse(user, emailVerified));
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request) {
        // Blacklist the current access token so it cannot authenticate again before its
        // natural expiry. The raw token was placed on the Authentication credentials by
        // JwtAuthenticationFilter; absence (e.g. logout without an Authorization header)
        // is tolerated and only the refresh token is revoked.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // Retained for defensive completeness — public path now requires authentication
        // (SecurityConfig enforces authenticated() on /logout).
        if (auth != null && auth.getCredentials() instanceof String rawToken) {
            try {
                JwtClaims claims = jwtTokenProvider.validateAndParse(rawToken);
                long remaining =
                        claims.expiresAt() == null
                                ? 0L
                                : claims.expiresAt().getEpochSecond()
                                        - Instant.now().getEpochSecond();
                // If this throws, the refresh token has already been revoked (REQUIRES_NEW
                // committed). The client receives 500; they should retry logout. The access
                // token remains valid until its natural expiry.
                tokenBlacklistService.blacklist(claims.jti(), remaining);
            } catch (AppException ignored) {
                // Token already invalid — refresh-token revoke below still proceeds.
            }
        }
        refreshTokenService.revoke(request.refreshToken());
    }

    @Override
    @Transactional
    public AuthResponse verifyEmail(String rawToken, HttpServletRequest httpRequest) {
        UUID userId;
        try {
            userId = tokenService.consumeEmailVerificationToken(rawToken);
        } catch (TokenNotFoundException | TokenExpiredException e) {
            throw new AppException(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID);
        }

        UserCredential credential =
                credentialRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));
        credential.setEmailVerified(true);
        credential.setEmailVerifiedAt(OffsetDateTime.now());
        credentialRepository.save(credential);

        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));
        return issueSession(user, true, httpRequest);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        UserCredential credential = credentialRepository.findByUserId(user.getId()).orElse(null);
        if (credential != null && credential.isEmailVerified()) {
            return;
        }

        authMailEventService.publishEmailVerificationRequested(user, null);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        long startNanos = System.nanoTime();
        try {
            authForgotPasswordEventService.recordForgotPasswordRequest(request.email());
        } finally {
            forgotPasswordTimingEqualizer.equalizeFrom(startNanos);
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId;
        try {
            userId = tokenService.consumePasswordResetToken(request.token());
        } catch (TokenNotFoundException | TokenExpiredException e) {
            throw new AppException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID));

        userStateValidator.enforceActive(user);

        UserCredential credential =
                credentialRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID));
        if (credential.getPasswordHash() == null) {
            throw new AppException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        }
        credential.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        credentialRepository.save(credential);

        refreshTokenService.revokeAllForUser(userId);

        authMailEventService.publishPasswordChanged(user);
    }

    @Override
    @Transactional
    public AuthResponse exchangeOAuth2Code(
            OAuth2ExchangeRequest request, HttpServletRequest httpRequest) {
        UUID userId = oauth2ExchangeCodeService.consumeExchangeCode(request.code());

        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ApiErrorCode.AUTH_OAUTH2_EXCHANGE_CODE_INVALID));

        userStateValidator.enforceActive(user);

        boolean emailVerified =
                credentialRepository
                        .findByUserId(user.getId())
                        .map(UserCredential::isEmailVerified)
                        // OAuth-authenticated users have their email verified by the IdP.
                        .orElse(true);

        return issueSession(user, emailVerified, httpRequest);
    }

    private AuthResponse issueSession(
            User user, boolean emailVerified, HttpServletRequest httpRequest) {
        String accessToken =
                jwtTokenProvider.generateAccessToken(
                        user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken =
                refreshTokenService.issue(
                        user.getId(),
                        null,
                        httpRequest.getHeader(HttpHeaders.USER_AGENT),
                        ipExtractor.extract(httpRequest));
        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtProperties.accessTokenTtl(),
                AuthResponse.BEARER,
                authMapper.toUserSummaryResponse(user, emailVerified));
    }
}
