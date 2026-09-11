package com.app.modules.support.service.impl;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.app.modules.support.config.SupportTurnstileProperties;

/**
 * Server-side verification of a Cloudflare Turnstile token.
 *
 * <p>Verification happens before anything is written. Turnstile proves the submitter is probably
 * not a bot; it proves nothing about the address they typed, which is what the separate email
 * confirmation step is for. Neither control is sufficient alone, which is why the public form
 * requires both.
 *
 * <p><strong>This verifier fails closed.</strong> When Cloudflare is unreachable, times out, or
 * answers anything other than a clear success, the submission is refused.
 *
 * <p>That choice is deliberate and is the less comfortable of the two. Failing open would keep
 * appeals flowing during a Cloudflare outage, but it would also mean that anyone able to cause a
 * timeout - which, for an outbound call from our own network, is not a high bar - can switch the
 * bot control off at will. A control that an attacker can disable by making one request slow is not
 * a control. Failing closed makes an outage visible and temporary: the public form stops, the two
 * authenticated paths and every signed appeal link in existing moderation mail keep working, so no
 * one who was actually mailed a decision loses their route to contest it.
 */
@Component
public class SupportTurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(SupportTurnstileVerifier.class);

    private final SupportTurnstileProperties properties;
    private final RestClient restClient;

    public SupportTurnstileVerifier(SupportTurnstileProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().requestFactory(requestFactory(properties)).build();
    }

    /**
     * Verifies one Turnstile token.
     *
     * @param token the token the client submitted, or null when the client sent none
     * @param remoteIp the caller's address as resolved by {@code IpExtractor}, or null
     * @return true only when Cloudflare positively confirms the token
     */
    public boolean verify(String token, String remoteIp) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        if (!StringUtils.hasText(properties.getSecretKey())) {
            // No secret configured. Refusing is the only safe reading: treating an unconfigured
            // control as a passing control would silently remove it in exactly the environment that
            // forgot to set it up.
            log.error("Turnstile secret is not configured; refusing the submission");
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey());
        form.add("response", token);
        if (StringUtils.hasText(remoteIp)) {
            form.add("remoteip", remoteIp);
        }
        try {
            Map<?, ?> body =
                    restClient
                            .post()
                            .uri(properties.getVerifyUrl())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(Map.class);
            return body != null && Boolean.TRUE.equals(body.get("success"));
        } catch (RuntimeException ex) {
            // Never logs the token. A failure here refuses the submission; see the class comment
            // for
            // why this direction was chosen.
            log.warn("Turnstile verification failed: {}", ex.getMessage());
            return false;
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            SupportTurnstileProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(nonNull(properties.getConnectTimeout(), Duration.ofSeconds(3)));
        factory.setReadTimeout(nonNull(properties.getReadTimeout(), Duration.ofSeconds(5)));
        return factory;
    }

    private static Duration nonNull(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }
}
