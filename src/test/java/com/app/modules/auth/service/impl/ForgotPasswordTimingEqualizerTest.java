package com.app.modules.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.app.modules.auth.config.ForgotPasswordTimingProperties;

class ForgotPasswordTimingEqualizerTest {

    @Test
    void equalizeFrom_sleepsRemainingTimeAfterWorkCompletes() {
        ForgotPasswordTimingProperties properties =
                properties(Duration.ofMillis(200), Duration.ZERO);
        AtomicLong sleptNanos = new AtomicLong();
        ForgotPasswordTimingEqualizer equalizer =
                new ForgotPasswordTimingEqualizer(
                        properties, () -> Duration.ofMillis(50).toNanos(), sleptNanos::set);

        equalizer.equalizeFrom(0L);

        assertThat(sleptNanos.get()).isEqualTo(Duration.ofMillis(150).toNanos());
    }

    @Test
    void equalizeFrom_doesNotSleepWhenWorkAlreadyExceedsFloor() {
        ForgotPasswordTimingProperties properties =
                properties(Duration.ofMillis(200), Duration.ZERO);
        AtomicLong sleptNanos = new AtomicLong();
        ForgotPasswordTimingEqualizer equalizer =
                new ForgotPasswordTimingEqualizer(
                        properties, () -> Duration.ofMillis(250).toNanos(), sleptNanos::set);

        equalizer.equalizeFrom(0L);

        assertThat(sleptNanos.get()).isZero();
    }

    @Test
    void equalizeFrom_restoresInterruptFlagWhenSleepIsInterrupted() {
        ForgotPasswordTimingProperties properties =
                properties(Duration.ofMillis(200), Duration.ZERO);
        ForgotPasswordTimingEqualizer equalizer =
                new ForgotPasswordTimingEqualizer(
                        properties,
                        () -> 0L,
                        nanos -> {
                            throw new InterruptedException("interrupted");
                        });

        equalizer.equalizeFrom(0L);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    private static ForgotPasswordTimingProperties properties(Duration min, Duration jitter) {
        ForgotPasswordTimingProperties properties = new ForgotPasswordTimingProperties();
        properties.setMinResponseTime(min);
        properties.setMaxJitter(jitter);
        return properties;
    }
}
