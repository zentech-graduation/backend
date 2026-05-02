package com.app.common.security.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.JwtProperties;
import com.app.common.security.RefreshTokenService;
import com.app.modules.auth.entity.RefreshToken;
import com.app.modules.auth.repository.RefreshTokenRepository;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String SHA_256 = "SHA-256";

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public String issue(UUID userId, String deviceId, String userAgent, String ipAddress) {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken entity =
                RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .tokenHash(sha256(rawToken))
                        .deviceId(deviceId)
                        .userAgent(userAgent)
                        .ipAddress(ipAddress)
                        .expiresAt(
                                OffsetDateTime.now().plusSeconds(jwtProperties.refreshTokenTtl()))
                        .build();
        repository.save(entity);
        return rawToken;
    }

    @Override
    @Transactional
    public RotationResult rotate(String rawToken, String ipAddress) {
        String hash = sha256(rawToken);
        RefreshToken existing =
                repository
                        .findByTokenHashAndRevokedAtIsNull(hash)
                        .orElseThrow(
                                () -> new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID));

        OffsetDateTime now = OffsetDateTime.now();
        if (existing.getExpiresAt().isBefore(now)) {
            repository.revokeByTokenHash(hash, now);
            throw new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
        }

        // Conditional UPDATE is the atomic gate. If zero rows update, a concurrent rotation
        // already won the race; treat this as a replay attempt and revoke every active session
        // for the user (token theft detection per OAuth 2.0 Security BCP).
        int revoked = repository.revokeByTokenHash(hash, now);
        if (revoked == 0) {
            repository.revokeAllActiveByUserId(existing.getUserId(), now);
            throw new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        String newRaw =
                issue(
                        existing.getUserId(),
                        existing.getDeviceId(),
                        existing.getUserAgent(),
                        ipAddress);
        return new RotationResult(newRaw, existing.getUserId());
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null) {
            return;
        }
        repository.revokeByTokenHash(sha256(rawToken), OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllActiveByUserId(userId, OffsetDateTime.now());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }
}
