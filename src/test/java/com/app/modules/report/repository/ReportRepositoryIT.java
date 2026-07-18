package com.app.modules.report.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.modules.report.entity.Report;
import com.app.modules.report.enums.ReportReason;
import com.app.modules.report.enums.ReportStatus;
import com.app.modules.report.enums.ReportType;

@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
class ReportRepositoryIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ReportRepository reportRepository;
    @Autowired private JdbcClient jdbcClient;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void save_duplicateReporterTypeEntity_failsAtDatabaseLayer() {
        UUID reporterId = insertUser("report_reporter", "report-reporter@example.com");
        UUID entityId = UUID.randomUUID();
        reportRepository.saveAndFlush(report(reporterId, entityId));

        // A second row with the same (reporter_id, report_type, entity_id) must be rejected by the
        // database so the concurrent-duplicate race cannot silently create duplicate reports that
        // the application-level check missed.
        assertThatThrownBy(() -> reportRepository.saveAndFlush(report(reporterId, entityId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Report report(UUID reporterId, UUID entityId) {
        return Report.builder()
                .reporterId(reporterId)
                .reportType(ReportType.POST)
                .reportReason(ReportReason.SPAM)
                .entityId(entityId)
                .status(ReportStatus.PENDING)
                .build();
    }

    private UUID insertUser(String username, String email) {
        return jdbcClient
                .sql(
                        """
						INSERT INTO users (username, email, role, status)
						VALUES (:username, :email, 'user', 'active')
						RETURNING id
						""")
                .param("username", username)
                .param("email", email)
                .query(UUID.class)
                .single();
    }
}
