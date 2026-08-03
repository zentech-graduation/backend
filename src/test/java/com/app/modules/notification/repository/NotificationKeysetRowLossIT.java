package com.app.modules.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.modules.notification.entity.Notification;

/**
 * Guards against keyset row loss when notifications share a boundary {@code created_at}. Pages with
 * a size that cuts into the tie-group and asserts every notification is returned exactly once; a
 * dropped row proves a strict-inequality regression, a duplicated row a non-strict one.
 */
@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotificationKeysetRowLossIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime SHARED_INSTANT =
            OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final int PAGE_SIZE = 2;

    private final NotificationRepository notificationRepository;
    private final JdbcClient jdbcClient;

    NotificationKeysetRowLossIT(
            NotificationRepository notificationRepository, JdbcClient jdbcClient) {
        this.notificationRepository = notificationRepository;
        this.jdbcClient = jdbcClient;
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void notifications_tieGroupOnCreatedAt_pagesEveryRowExactlyOnce() {
        UUID recipient = insertUser("notif_recipient");
        List<UUID> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID notificationId = insertNotification(recipient, SHARED_INSTANT);
            expected.add(notificationId);
        }

        List<UUID> seen = new ArrayList<>();
        List<Notification> page = notificationRepository.findFirstByRecipient(recipient, page());
        int guard = 0;
        while (!page.isEmpty() && guard++ < 100) {
            page.forEach(n -> seen.add(n.getId()));
            Notification last = page.get(page.size() - 1);
            page =
                    notificationRepository.findByRecipientBefore(
                            recipient, last.getCreatedAt(), last.getId(), page());
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static PageRequest page() {
        return PageRequest.of(0, PAGE_SIZE);
    }

    private UUID insertUser(String username) {
        return jdbcClient
                .sql(
                        """
						INSERT INTO users(username, email, display_name)
						VALUES (:username, :email, :displayName)
						RETURNING id
						""")
                .param("username", username)
                .param("email", username + "@example.com")
                .param("displayName", username)
                .query(UUID.class)
                .single();
    }

    private UUID insertNotification(UUID recipientId, OffsetDateTime createdAt) {
        return jdbcClient
                .sql(
                        "INSERT INTO notifications(recipient_id, type, created_at)"
                                + " VALUES (:recipientId, 'follow', :createdAt) RETURNING id")
                .param("recipientId", recipientId)
                .param("createdAt", createdAt)
                .query(UUID.class)
                .single();
    }
}
