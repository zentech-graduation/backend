package com.app.modules.mail.config;

import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when a local mail sink is selected outside development.
 *
 * <p>Profile-specific YAML only sets a default, and an operating-system environment variable
 * outranks it, so a stray {@code APP_MAIL_TRANSPORT=smtp} in production would divert every outbound
 * message into a local sink without raising an error anywhere. This reads the resolved value rather
 * than any single file so the override is caught, and reports the resolved transport alongside the
 * active profiles on both the success and the failure path.
 */
@Component
public class MailTransportGuard {

    static final String TRANSPORT_PROPERTY = "app.mail.transport";
    static final String SMTP_TRANSPORT = "smtp";
    static final String DEVELOPMENT_PROFILE = "dev";

    private static final Logger log = LoggerFactory.getLogger(MailTransportGuard.class);

    private final Environment environment;

    public MailTransportGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verifyTransportIsPermittedHere() {
        String transport = environment.getProperty(TRANSPORT_PROPERTY);
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
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
