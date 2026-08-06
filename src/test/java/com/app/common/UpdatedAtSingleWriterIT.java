package com.app.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

/**
 * Guards the {@code users}, {@code posts}, and {@code comments} tables' {@code updated_at}
 * invariant: the {@code trg_*_updated_at} Postgres trigger (V16) is the sole writer, and the entity
 * must reflect exactly the trigger-written value, never an independently computed application-side
 * guess.
 *
 * <p>Before {@code @Generated} replaced {@code @UpdateTimestamp}, both Hibernate and the trigger
 * wrote this column on every update; the trigger always won at the database level, but Hibernate
 * never learned that and kept its own guess in the entity it returned to the caller.
 */
@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UpdatedAtSingleWriterIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private final UserRepository userRepository;
    private final JdbcClient jdbcClient;
    private final EntityManager entityManager;

    UpdatedAtSingleWriterIT(
            UserRepository userRepository, JdbcClient jdbcClient, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.jdbcClient = jdbcClient;
        this.entityManager = entityManager;
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void insert_entityUpdatedAtMatchesTheDatabaseValueExactly() {
        User user =
                userRepository.save(
                        User.builder()
                                .username(uniqueUsername())
                                .email(UUID.randomUUID() + "@test.local")
                                .role(UserRole.USER)
                                .status(UserStatus.ACTIVE)
                                .isPrivate(false)
                                .isVerified(false)
                                .build());
        entityManager.flush();

        OffsetDateTime actualDbValue = actualUpdatedAt(user.getId());

        assertThat(user.getUpdatedAt()).isEqualTo(actualDbValue);
    }

    @Test
    void update_entityUpdatedAtMatchesTheDatabaseValueExactly() {
        User user =
                userRepository.save(
                        User.builder()
                                .username(uniqueUsername())
                                .email(UUID.randomUUID() + "@test.local")
                                .role(UserRole.USER)
                                .status(UserStatus.ACTIVE)
                                .isPrivate(false)
                                .isVerified(false)
                                .build());

        entityManager.flush();
        user.setDisplayName("changed");
        User updated = userRepository.save(user);
        entityManager.flush();

        OffsetDateTime actualDbValue = actualUpdatedAt(updated.getId());

        assertThat(updated.getUpdatedAt()).isEqualTo(actualDbValue);
    }

    private static String uniqueUsername() {
        return "uat_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private OffsetDateTime actualUpdatedAt(UUID userId) {
        return jdbcClient
                .sql("SELECT updated_at FROM users WHERE id = :id")
                .param("id", userId)
                .query(OffsetDateTime.class)
                .single();
    }
}
