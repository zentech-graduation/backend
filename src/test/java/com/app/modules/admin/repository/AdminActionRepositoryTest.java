package com.app.modules.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class AdminActionRepositoryTest {

    @Test
    void repository_appendOnlyContract_exposesNoUpdateOrDeleteMethods() {
        assertThat(Arrays.stream(AdminActionRepository.class.getMethods()).map(Method::getName))
                .doesNotContain(
                        "save", "saveAll", "delete", "deleteById", "deleteAll", "deleteAllById");
    }
}
