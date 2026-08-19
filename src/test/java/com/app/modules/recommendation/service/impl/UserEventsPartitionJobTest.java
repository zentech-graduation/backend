package com.app.modules.recommendation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserEventsPartitionJobTest {

    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

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
    void ensureUpcomingPartition_coversCurrentMonthAndTheNextTwo() {
        // The window is ensured whole rather than only at its far edge. A job that created just the
        // month at +2 left a permanent hole whenever a single run was missed, and a hole in this
        // table cannot be filled later once a row has landed in user_events_default.
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        List<String> expected =
                List.of(
                        "user_events_" + currentMonth.format(SUFFIX),
                        "user_events_" + currentMonth.plusMonths(1).format(SUFFIX),
                        "user_events_" + currentMonth.plusMonths(2).format(SUFFIX));

        job.ensureUpcomingPartition();

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).execute(ddl.capture());
        assertThat(ddl.getAllValues())
                .hasSize(3)
                .satisfies(
                        statements -> {
                            for (int i = 0; i < expected.size(); i++) {
                                assertThat(statements.get(i)).contains(expected.get(i));
                            }
                        });
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

    @Test
    void ensureUpcomingPartition_oneFailingMonthDoesNotStopTheRest() {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        String failingMonth = "user_events_" + currentMonth.format(SUFFIX);
        doThrow(new DataIntegrityViolationException("default partition would be violated"))
                .when(jdbcTemplate)
                .execute(contains(failingMonth));

        assertThatCode(() -> job.ensureUpcomingPartition()).doesNotThrowAnyException();

        verify(jdbcTemplate, times(3)).execute(anyString());
    }
}
