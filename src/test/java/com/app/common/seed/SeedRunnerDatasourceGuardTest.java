package com.app.common.seed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SeedRunnerDatasourceGuardTest {

    @Test
    void refusesNonLocalDatasourceUrl() {
        assertThatThrownBy(
                        () ->
                                SeedRunner.assertLocalDatasource(
                                        "jdbc:postgresql://prod-db.example.com:5432/luvax"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local");
    }

    @Test
    void allowsLocalhostVariants() {
        SeedRunner.assertLocalDatasource("jdbc:postgresql://localhost:5432/luvax");
        SeedRunner.assertLocalDatasource("jdbc:postgresql://127.0.0.1:5432/luvax");
        SeedRunner.assertLocalDatasource("jdbc:postgresql://[::1]:5432/luvax");
    }
}
