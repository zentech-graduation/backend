package com.app.common.security.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtProperties;
import com.app.common.security.service.RefreshTokenService;
import com.app.modules.auth.entity.RefreshToken;
import com.app.modules.auth.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String SHA_256 = "SHA-256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public String issue(UUID userId, String deviceId, String userAgent, String ipAddress) {
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
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
    // Rotation must commit independently so the old token is always durably revoked even if the
    // outer refresh transaction rolls back after a subsequent account-status check.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RotationResult rotate(String rawToken, String ipAddress) {
        String hash = sha256(rawToken);
        RefreshToken existing = repository.findByTokenHash(hash).orElse(null);

        if (existing == null) {
            // Equalize timing with the known-revoked path (which performs additional UPDATEs to
            // revoke all active sessions). A 2-8ms randomized sleep masks the DB-round-trip gap;
            // this is a coarse mitigation, not a constant-time guarantee.
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(2, 8));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            throw new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        if (existing.getRevokedAt() != null) {
            // Replay of an already-revoked refresh token is the OAuth 2.0 Security BCP signal for
            // token theft -- without this log, revoking every session for the user is invisible.
            log.warn("Refresh token replay detected for userId={}", existing.getUserId());
            repository.revokeAllActiveByUserId(existing.getUserId(), OffsetDateTime.now());
            throw new AppException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

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
            // Lost the conditional-UPDATE race: same replay-detection signal as an already-revoked
            // token above -- log before revoking every session for the user.
            log.warn(
                    "Refresh token replay detected (concurrent rotation race) for userId={}",
                    existing.getUserId());
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
    // Revocation must commit independently so that a banned or suspended user's newly rotated
    // token is durably revoked even if the outer refresh transaction rolls back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
