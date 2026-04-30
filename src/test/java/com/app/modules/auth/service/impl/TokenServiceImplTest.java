package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.exception.TokenAlreadyUsedException;
import com.app.common.exception.TokenExpiredException;
import com.app.common.exception.TokenNotFoundException;
import com.app.modules.auth.entity.EmailVerificationToken;
import com.app.modules.auth.entity.PasswordResetToken;
import com.app.modules.auth.repository.EmailVerificationTokenRepository;
import com.app.modules.auth.repository.PasswordResetTokenRepository;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock private EmailVerificationTokenRepository emailRepository;

    @Mock private PasswordResetTokenRepository passwordRepository;

    @InjectMocks private TokenServiceImpl service;

    @Test
    void createEmailVerificationToken_invalidatesPendingTokens() {
        UUID userId = UUID.randomUUID();

        service.createEmailVerificationToken(userId);

        verify(emailRepository).deleteByUserIdAndUsedAtIsNull(userId);
        verify(emailRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void createEmailVerificationToken_persistsHashedTokenWith24HourExpiry() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();

        String rawToken = service.createEmailVerificationToken(userId);

        ArgumentCaptor<EmailVerificationToken> captor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken));
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);

        Duration ttl = Duration.between(before, saved.getExpiresAt());
        assertThat(ttl)
                .isBetween(
                        Duration.ofHours(23).plusMinutes(59), Duration.ofHours(24).plusSeconds(5));
    }

    @Test
    void createEmailVerificationToken_returnsUniqueRawTokens() {
        UUID userId = UUID.randomUUID();

        String first = service.createEmailVerificationToken(userId);
        String second = service.createEmailVerificationToken(userId);

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void consumeEmailVerificationToken_marksUsedAtOnHappyPath() {
        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken stored =
                EmailVerificationToken.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .tokenHash(sha256Hex(rawToken))
                        .expiresAt(OffsetDateTime.now().plusMinutes(5))
                        .usedAt(null)
                        .build();
        when(emailRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(stored));

        service.consumeEmailVerificationToken(rawToken);

        ArgumentCaptor<EmailVerificationToken> captor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedAt()).isNotNull();
    }

    @Test
    void consumeEmailVerificationToken_throwsWhenHashAbsent() {
        when(emailRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeEmailVerificationToken("does-not-exist"))
                .isInstanceOf(TokenNotFoundException.class);
        verify(emailRepository, never()).save(any());
    }

    @Test
    void consumeEmailVerificationToken_throwsWhenExpired() {
        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken expired =
                EmailVerificationToken.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .tokenHash(sha256Hex(rawToken))
                        .expiresAt(OffsetDateTime.now().minusSeconds(1))
                        .usedAt(null)
                        .build();
        when(emailRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.consumeEmailVerificationToken(rawToken))
                .isInstanceOf(TokenExpiredException.class);
        verify(emailRepository, never()).save(any());
    }

    @Test
    void consumeEmailVerificationToken_throwsWhenAlreadyUsed() {
        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken used =
                EmailVerificationToken.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .tokenHash(sha256Hex(rawToken))
                        .expiresAt(OffsetDateTime.now().plusMinutes(5))
                        .usedAt(OffsetDateTime.now().minusMinutes(1))
                        .build();
        when(emailRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.consumeEmailVerificationToken(rawToken))
                .isInstanceOf(TokenAlreadyUsedException.class);
        verify(emailRepository, never()).save(any());
    }

    @Test
    void createPasswordResetToken_invalidatesPendingAndPersistsHashed15MinToken() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime before = OffsetDateTime.now();

        String rawToken = service.createPasswordResetToken(userId);

        verify(passwordRepository).deleteByUserIdAndUsedAtIsNull(userId);
        ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken));

        Duration ttl = Duration.between(before, saved.getExpiresAt());
        assertThat(ttl)
                .isBetween(
                        Duration.ofMinutes(14).plusSeconds(50),
                        Duration.ofMinutes(15).plusSeconds(5));
    }

    @Test
    void consumePasswordResetToken_marksUsedAtOnHappyPath() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken stored =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .tokenHash(sha256Hex(rawToken))
                        .expiresAt(OffsetDateTime.now().plusMinutes(5))
                        .usedAt(null)
                        .build();
        when(passwordRepository.findByTokenHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(stored));

        service.consumePasswordResetToken(rawToken);

        verify(passwordRepository, times(1)).save(any(PasswordResetToken.class));
        assertThat(stored.getUsedAt()).isNotNull();
    }

    @Test
    void consumePasswordResetToken_throwsWhenHashAbsent() {
        when(passwordRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumePasswordResetToken("nope"))
                .isInstanceOf(TokenNotFoundException.class);
    }

    @Test
    void consumePasswordResetToken_throwsWhenExpired() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken expired =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .tokenHash(sha256Hex(rawToken))
                        .expiresAt(OffsetDateTime.now().minusSeconds(1))
                        .build();
        when(passwordRepository.findByTokenHash(sha256Hex(rawToken)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.consumePasswordResetToken(rawToken))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void consumePasswordResetToken_throwsWhenAlreadyUsed() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken used =
                PasswordResetToken.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.randomUUID())
                        .tokenHash(sha256Hex(rawToken))
                        .expiresAt(OffsetDateTime.now().plusMinutes(5))
                        .usedAt(OffsetDateTime.now())
                        .build();
        when(passwordRepository.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.consumePasswordResetToken(rawToken))
                .isInstanceOf(TokenAlreadyUsedException.class);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
