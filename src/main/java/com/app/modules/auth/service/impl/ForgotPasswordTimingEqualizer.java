package com.app.modules.auth.service.impl;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.app.modules.auth.config.ForgotPasswordTimingProperties;

@Component
public class ForgotPasswordTimingEqualizer {

    private final ForgotPasswordTimingProperties properties;
    private final LongSupplier nanoTimeSupplier;
    private final Sleeper sleeper;

    @Autowired
    public ForgotPasswordTimingEqualizer(ForgotPasswordTimingProperties properties) {
        this(properties, System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    ForgotPasswordTimingEqualizer(
            ForgotPasswordTimingProperties properties,
            LongSupplier nanoTimeSupplier,
            Sleeper sleeper) {
        this.properties = properties;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.sleeper = sleeper;
    }

    public void equalizeFrom(long startNanos) {
        long targetNanos = targetDuration().toNanos();
        if (targetNanos <= 0L) {
            return;
        }

        long elapsedNanos = Math.max(0L, nanoTimeSupplier.getAsLong() - startNanos);
        long remainingNanos = targetNanos - elapsedNanos;
        if (remainingNanos <= 0L) {
            return;
        }

        try {
            sleeper.sleep(remainingNanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration targetDuration() {
        Duration minResponseTime = properties.resolvedMinResponseTime();
        Duration maxJitter = properties.resolvedMaxJitter();
        if (maxJitter.isZero()) {
            return minResponseTime;
        }

        long jitterNanos = ThreadLocalRandom.current().nextLong(maxJitter.toNanos() + 1L);
        return minResponseTime.plusNanos(jitterNanos);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }
}
