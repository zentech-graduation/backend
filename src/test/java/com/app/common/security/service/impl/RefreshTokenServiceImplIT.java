package com.app.common.security.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtProperties;
import com.app.common.security.service.RefreshTokenService;

/**
 * Covers the durability of the revocations {@code rotate} performs on a rejected refresh.
 *
 * <p>The test must run outside a transaction. Each revocation happens inside {@code rotate}'s own
 * {@code REQUIRES_NEW} transaction and is followed immediately by an {@code AppException}, so
 * whether it survives is decided by that transaction's rollback rules. A test that wrapped the call
 * in its own transaction would not exercise them.
 */
@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
@Import({RefreshTokenServiceImpl.class, RefreshTokenServiceImplIT.JwtPropertiesTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenServiceImplIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @TestConfiguration
    static class JwtPropertiesTestConfig {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-secret-at-least-32-characters-long", "luvax", "luvax", 900, 2_592_000);
        }
    }

    @Test
    void rotate_replayOfRevokedToken_revokesEveryActiveSessionForThatUser() {
        UUID userId = insertUser();
        String first = refreshTokenService.issue(userId, "device-1", "agent", "127.0.0.1");
        refreshTokenService.issue(userId, "device-2", "agent", "127.0.0.1");
        refreshTokenService.issue(userId, "device-3", "agent", "127.0.0.1");
        refreshTokenService.rotate(first, "127.0.0.1");
        assertThat(activeSessions(userId)).isEqualTo(3);

        assertThatThrownBy(() -> refreshTokenService.rotate(first, "127.0.0.1"))
                .isInstanceOf(AppException.class);

        // The revoking UPDATE runs immediately before the throw. Before noRollbackFor was added the
        // exception rolled it back, so this count stayed at 3 while the log claimed every session
        // had been revoked.
        assertThat(activeSessions(userId)).isZero();
    }

    @Test
    void rotate_replayOfRevokedToken_leavesOtherUsersSessionsAlone() {
        UUID victim = insertUser();
        UUID bystander = insertUser();
        String victimToken = refreshTokenService.issue(victim, "device-1", "agent", "127.0.0.1");
        refreshTokenService.issue(bystander, "device-1", "agent", "127.0.0.1");
        refreshTokenService.rotate(victimToken, "127.0.0.1");

        assertThatThrownBy(() -> refreshTokenService.rotate(victimToken, "127.0.0.1"))
                .isInstanceOf(AppException.class);

        assertThat(activeSessions(victim)).isZero();
        assertThat(activeSessions(bystander)).isEqualTo(1);
    }

    @Test
    void rotate_rotatedTokenReplayed_alsoInvalidatesTheTokenItWasRotatedInto() {
        UUID userId = insertUser();
        String original = refreshTokenService.issue(userId, "device-1", "agent", "127.0.0.1");
        String rotated = refreshTokenService.rotate(original, "127.0.0.1").newRawToken();

        assertThatThrownBy(() -> refreshTokenService.rotate(original, "127.0.0.1"))
                .isInstanceOf(AppException.class);

        // The whole point of the control: a thief who rotated first must not keep a working session
        // once the legitimate client's stale token trips the replay check.
        assertThatThrownBy(() -> refreshTokenService.rotate(rotated, "127.0.0.1"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rotate_expiredToken_commitsThatTokensRevocation() {
        UUID userId = insertUser();
        String token = refreshTokenService.issue(userId, "device-1", "agent", "127.0.0.1");
        expire(userId);

        assertThatThrownBy(() -> refreshTokenService.rotate(token, "127.0.0.1"))
                .isInstanceOf(AppException.class);

        assertThat(revokedSessions(userId)).isEqualTo(1);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        entityManager
                .createNativeQuery(
                        "INSERT INTO users (id, username, email) VALUES (:id, :username, :email)")
                .setParameter("id", id)
                .setParameter("username", "u" + suffix)
                .setParameter("email", "u" + suffix + "@example.test")
                .executeUpdate();
        return id;
    }

    private void expire(UUID userId) {
        entityManager
                .createNativeQuery(
                        "UPDATE refresh_tokens SET expires_at = :past WHERE user_id = :userId")
                .setParameter("past", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private long activeSessions(UUID userId) {
        return count(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = :userId AND revoked_at IS NULL",
                userId);
    }

    private long revokedSessions(UUID userId) {
        return count(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = :userId AND revoked_at IS NOT NULL",
                userId);
    }

    private long count(String sql, UUID userId) {
        return ((Number)
                        entityManager
                                .createNativeQuery(sql)
                                .setParameter("userId", userId)
                                .getSingleResult())
                .longValue();
    }
}
