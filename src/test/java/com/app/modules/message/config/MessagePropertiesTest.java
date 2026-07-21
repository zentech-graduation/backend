package com.app.modules.message.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagePropertiesTest {

    @Test
    void constructor_positiveValues_keepsGivenValues() {
        MessageProperties properties = new MessageProperties(48, false, 100);

        assertThat(properties.idempotencyTtlHours()).isEqualTo(48);
        assertThat(properties.groupChatEnabled()).isFalse();
        assertThat(properties.maxGroupParticipants()).isEqualTo(100);
    }

    @Test
    void constructor_nonPositiveIdempotencyTtlHours_defaultsTo24() {
        assertThat(new MessageProperties(0, true, 256).idempotencyTtlHours()).isEqualTo(24);
        assertThat(new MessageProperties(-5, true, 256).idempotencyTtlHours()).isEqualTo(24);
    }

    @Test
    void constructor_nonPositiveMaxGroupParticipants_defaultsTo256() {
        assertThat(new MessageProperties(24, true, 0).maxGroupParticipants()).isEqualTo(256);
        assertThat(new MessageProperties(24, true, -1).maxGroupParticipants()).isEqualTo(256);
    }
}
