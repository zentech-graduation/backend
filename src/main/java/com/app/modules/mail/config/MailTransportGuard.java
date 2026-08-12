package com.app.modules.mail.config;

import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when the mail transport is unusable or when a local mail sink is selected
 * outside development.
 *
 * <p>Profile-specific YAML only sets a default, and an operating-system environment variable
 * outranks it, so a stray {@code APP_MAIL_TRANSPORT=smtp} in production would divert every outbound
 * message into a local sink without raising an error anywhere. This reads the resolved value rather
 * than any single file so the override is caught, and reports the resolved transport alongside the
 * active profiles on both the success and the failure path.
 *
 * <p>An unrecognised value and an absent property are both rejected here as well. Neither registers
 * a {@code MailSender}, so without this check startup failed later against a generic missing-bean
 * message that named the transport property nowhere and gave an operator no reason to look at it.
 */
@Component
public class MailTransportGuard {

    static final String TRANSPORT_PROPERTY = "app.mail.transport";
    static final String SMTP_TRANSPORT = "smtp";
    static final String RESEND_TRANSPORT = "resend";
    static final String DEVELOPMENT_PROFILE = "dev";

    /**
     * The values that register a sender, one per {@code @ConditionalOnProperty} on the transport
     * configurations. Matched case-insensitively and without trimming, which is exactly how
     * {@code @ConditionalOnProperty} matches, so this guard accepts a value if and only if some
     * configuration would have wired a bean for it.
     */
    private static final List<String> ACCEPTED_TRANSPORTS =
            List.of(RESEND_TRANSPORT, SMTP_TRANSPORT);

    private static final Logger log = LoggerFactory.getLogger(MailTransportGuard.class);

    private final Environment environment;

    public MailTransportGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verifyTransportIsPermittedHere() {
        String transport = environment.getProperty(TRANSPORT_PROPERTY);
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (ACCEPTED_TRANSPORTS.stream()
                .noneMatch(accepted -> accepted.equalsIgnoreCase(transport))) {
            throw new IllegalStateException(
                    "Mail transport property "
                            + TRANSPORT_PROPERTY
                            + " must be one of "
                            + ACCEPTED_TRANSPORTS
                            + ", but found "
                            + (transport == null ? "no value at all" : "'" + transport + "'")
                            + ". No mail sender is registered for that, so leaving it unset or"
                            + " misspelled fails startup later against a missing bean instead of"
                            + " here. Active profiles are "
                            + activeProfiles
                            + ".");
        }
        if (SMTP_TRANSPORT.equalsIgnoreCase(transport)
                && !activeProfiles.contains(DEVELOPMENT_PROFILE)) {
            throw new IllegalStateException(
                    "Mail transport '"
                            + transport
                            + "' delivers to a local sink and is permitted only when the '"
                            + DEVELOPMENT_PROFILE
                            + "' profile is active, but the active profiles are "
                            + activeProfiles
                            + ". Set "
                            + TRANSPORT_PROPERTY
                            + "=resend for this environment, or remove the environment override"
                            + " that selected it.");
        }
        log.info(
                "Mail transport resolved | transport: {} | active profiles: {}",
                transport,
                activeProfiles);
    }
}
