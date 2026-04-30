package com.app.modules.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.exception.TokenAlreadyUsedException;
import com.app.common.exception.TokenExpiredException;
import com.app.common.exception.TokenNotFoundException;
import com.app.modules.auth.entity.EmailVerificationToken;
import com.app.modules.auth.entity.PasswordResetToken;
import com.app.modules.auth.repository.EmailVerificationTokenRepository;
import com.app.modules.auth.repository.PasswordResetTokenRepository;
import com.app.modules.auth.service.TokenService;

@Service
public class TokenServiceImpl implements TokenService {

    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);
    private static final String SHA_256 = "SHA-256";

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public TokenServiceImpl(
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Override
    @Transactional
    public String createEmailVerificationToken(UUID userId) {
        emailVerificationTokenRepository.deleteByUserIdAndUsedAtIsNull(userId);
        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken entity =
                EmailVerificationToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .tokenHash(sha256(rawToken))
                        .expiresAt(OffsetDateTime.now().plus(EMAIL_VERIFICATION_TTL))
                        .build();
        emailVerificationTokenRepository.save(entity);
        return rawToken;
    }

    @Override
    @Transactional
    public void consumeEmailVerificationToken(String rawToken) {
        String hash = sha256(rawToken);
        EmailVerificationToken token =
                emailVerificationTokenRepository
                        .findByTokenHash(hash)
                        .orElseThrow(
                                () ->
                                        new TokenNotFoundException(
                                                "Email verification token not found"));
        assertConsumable(token.getExpiresAt(), token.getUsedAt(), "Email verification token");
        token.setUsedAt(OffsetDateTime.now());
        emailVerificationTokenRepository.save(token);
    }

    @Override
    @Transactional
    public String createPasswordResetToken(UUID userId) {
        passwordResetTokenRepository.deleteByUserIdAndUsedAtIsNull(userId);
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken entity =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .tokenHash(sha256(rawToken))
                        .expiresAt(OffsetDateTime.now().plus(PASSWORD_RESET_TTL))
                        .build();
        passwordResetTokenRepository.save(entity);
        return rawToken;
    }

    @Override
    @Transactional
    public void consumePasswordResetToken(String rawToken) {
        String hash = sha256(rawToken);
        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByTokenHash(hash)
                        .orElseThrow(
                                () -> new TokenNotFoundException("Password reset token not found"));
        assertConsumable(token.getExpiresAt(), token.getUsedAt(), "Password reset token");
        token.setUsedAt(OffsetDateTime.now());
        passwordResetTokenRepository.save(token);
    }

    private static void assertConsumable(
            OffsetDateTime expiresAt, OffsetDateTime usedAt, String tokenLabel) {
        if (usedAt != null) {
            throw new TokenAlreadyUsedException(tokenLabel + " has already been used");
        }
        if (expiresAt.isBefore(OffsetDateTime.now())) {
            throw new TokenExpiredException(tokenLabel + " has expired");
        }
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
