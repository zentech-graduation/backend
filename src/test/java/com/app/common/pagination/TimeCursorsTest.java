package com.app.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TimeCursorsTest {

    @Test
    void toMicros_thenFromMicros_preservesMicrosecondInstant() {
        OffsetDateTime time =
                OffsetDateTime.of(2026, 7, 25, 12, 34, 56, 789_012_000, ZoneOffset.UTC);

        long micros = TimeCursors.toMicros(time);

        assertThat(TimeCursors.fromMicros(micros).toInstant()).isEqualTo(time.toInstant());
    }

    @Test
    void toMicros_truncatesSubMicrosecondNanos() {
        OffsetDateTime withNanos =
                OffsetDateTime.of(2026, 7, 25, 12, 0, 0, 123_456_789, ZoneOffset.UTC);

        long micros = TimeCursors.toMicros(withNanos);

        assertThat(micros % 1L).isZero();
        assertThat(TimeCursors.fromMicros(micros).getNano()).isEqualTo(123_456_000);
    }

    @Test
    void fromMicros_isUtc() {
        assertThat(TimeCursors.fromMicros(0L).getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void toMicros_equalInstantsInDifferentOffsets_areEqual() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 7, 25, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime plusTwo = OffsetDateTime.of(2026, 7, 25, 14, 0, 0, 0, ZoneOffset.ofHours(2));

        assertThat(TimeCursors.toMicros(utc)).isEqualTo(TimeCursors.toMicros(plusTwo));
    }
}
