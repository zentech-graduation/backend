package com.app.modules.message.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagePropertiesTest {

    @Test
    void constructor_positiveValue_keepsGivenValue() {
        assertThat(new MessageProperties(48).idempotencyTtlHours()).isEqualTo(48);
    }

    @Test
    void constructor_nonPositiveIdempotencyTtlHours_defaultsTo24() {
        assertThat(new MessageProperties(0).idempotencyTtlHours()).isEqualTo(24);
        assertThat(new MessageProperties(-5).idempotencyTtlHours()).isEqualTo(24);
    }
}
