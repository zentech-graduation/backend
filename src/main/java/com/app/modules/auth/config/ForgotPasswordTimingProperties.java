package com.app.modules.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Timing floor used to reduce forgot-password account-enumeration side channels. */
@ConfigurationProperties(prefix = "app.auth.forgot-password")
@Getter
@Setter
public class ForgotPasswordTimingProperties {

    private Duration minResponseTime = Duration.ofMillis(200);
    private Duration maxJitter = Duration.ofMillis(50);

    public Duration resolvedMinResponseTime() {
        if (minResponseTime == null || minResponseTime.isNegative()) {
            return Duration.ZERO;
        }
        return minResponseTime;
    }

    public Duration resolvedMaxJitter() {
        if (maxJitter == null || maxJitter.isNegative()) {
            return Duration.ZERO;
        }
        return maxJitter;
    }
}
