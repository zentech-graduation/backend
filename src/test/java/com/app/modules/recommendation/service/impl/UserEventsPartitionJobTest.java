package com.app.modules.recommendation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserEventsPartitionJobTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private UserEventsPartitionJob job;

    @BeforeEach
    void setUp() {
        job = new UserEventsPartitionJob(jdbcTemplate);
    }

    @Test
    void createMonthlyPartitionIfAbsent_emitsIfNotExistsPartitionDdlForMonthRange() {
        job.createMonthlyPartitionIfAbsent(LocalDate.of(2026, 9, 15));

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertThat(ddl.getValue())
                .contains("CREATE TABLE IF NOT EXISTS user_events_2026_09")
                .contains("PARTITION OF user_events")
                .contains("FROM ('2026-09-01') TO ('2026-10-01')");
    }

    @Test
    void ensureUpcomingPartition_targetsMonthTwoMonthsAhead() {
        LocalDate expectedMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(2);
        String expectedName =
                "user_events_" + expectedMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"));

        job.ensureUpcomingPartition();

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertThat(ddl.getValue()).contains(expectedName);
    }

    @Test
    void createMonthlyPartitionIfAbsent_repeatedRunsEmitSameIdempotentDdl() {
        // The IF NOT EXISTS clause makes a repeat run for the same month a database-level no-op;
        // at the JDBC boundary the identical idempotent DDL is emitted each time.
        job.createMonthlyPartitionIfAbsent(LocalDate.of(2026, 9, 1));
        job.createMonthlyPartitionIfAbsent(LocalDate.of(2026, 9, 1));

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(ddl.capture());
        assertThat(ddl.getAllValues())
                .allMatch(sql -> sql.contains("CREATE TABLE IF NOT EXISTS user_events_2026_09"));
    }
}
