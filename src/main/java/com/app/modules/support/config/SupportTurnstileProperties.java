package com.app.modules.support.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds the Cloudflare Turnstile configuration from {@code app.support.turnstile.*}.
 *
 * <p>The secret defaults to empty in the base profile and to Cloudflare's published always-passes
 * test key in {@code dev}. An empty secret makes the verifier refuse every submission rather than
 * admit every submission, so a deployment that forgets to configure the real key fails the public
 * form closed instead of silently removing the control.
 */
@ConfigurationProperties(prefix = "app.support.turnstile")
@Getter
@Setter
public class SupportTurnstileProperties {

    private String secretKey = "";

    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    /** Short: Cloudflare is on the critical path of a user-facing submit. */
    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(5);
}
