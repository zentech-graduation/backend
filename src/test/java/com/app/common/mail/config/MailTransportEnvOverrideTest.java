package com.app.common.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

class MailTransportEnvOverrideTest {

    private static final String TRANSPORT_PROPERTY = "app.mail.transport";
    private static final String OVERRIDE_ENV_KEY = "APP_MAIL_TRANSPORT";

    // Neither method reads the developer's own .env content: a source (defaultProperties, or
    // the Surefire systemPropertyVariables pin) is deliberately supplied that determines the
    // resolved value regardless of what .env does or does not contain. A .env with
    // APP_MAIL_TRANSPORT=resend already set agrees with the defaultProperties override below, so
    // it changes nothing either way.
    @Test
    void devProfile_resolvesTransportFromDefaultProperties_whenSystemPropertiesPinIsNeutralized() {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

        SpringApplication application = new SpringApplication(EmptyPrimarySource.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("dev");
        application.setDefaultProperties(Map.of(OVERRIDE_ENV_KEY, "resend"));
        application.setEnvironment(environment);

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getEnvironment().getProperty(TRANSPORT_PROPERTY))
                    .isEqualTo("resend");
        }
    }

    // Leaves the real systemProperties source in place, so the Surefire pin from pom.xml
    // (APP_MAIL_TRANSPORT=noop) outranks the defaultProperties override below for the
    // placeholder application-dev.yml reads, proving the pin actually protects the suite rather
    // than merely appearing to.
    @Test
    void devProfile_resolvesTransportFromSurefirePin_whenSystemPropertiesAreLeftIntact() {
        SpringApplication application = new SpringApplication(EmptyPrimarySource.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("dev");
        application.setDefaultProperties(Map.of(OVERRIDE_ENV_KEY, "resend"));

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getEnvironment().getProperty(TRANSPORT_PROPERTY)).isEqualTo("noop");
        }
    }

    private static class EmptyPrimarySource {}
}
