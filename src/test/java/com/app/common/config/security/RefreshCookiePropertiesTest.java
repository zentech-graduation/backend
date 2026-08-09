package com.app.common.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class RefreshCookiePropertiesTest {

    @Test
    void bind_noOverrides_appliesDocumentedDefaults() {
        RefreshCookieProperties properties = bind(new MockEnvironment());

        assertThat(properties.name()).isEqualTo("luvax_refresh");
        assertThat(properties.path()).isEqualTo("/api/v1/auth");
        assertThat(properties.secure()).isTrue();
        assertThat(properties.sameSite()).isEqualTo("Lax");
    }

    @Test
    void bind_sameSiteNoneWithSecureFalse_isRejected() {
        // A browser silently discards SameSite=None without Secure. Left unvalidated, this
        // misconfiguration logs out every user on reload with no server-side signal at all.
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.security.refresh-cookie.same-site", "None");
        environment.setProperty("app.security.refresh-cookie.secure", "false");

        assertThatThrownBy(() -> bind(environment)).isInstanceOf(BindException.class);
    }

    @Test
    void bind_sameSiteNoneWithSecureTrue_isAccepted() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.security.refresh-cookie.same-site", "None");
        environment.setProperty("app.security.refresh-cookie.secure", "true");

        assertThatCode(() -> bind(environment)).doesNotThrowAnyException();
    }

    @Test
    void bind_sameSiteLaxWithSecureFalse_isAcceptedForPlainHttpLocalhost() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.security.refresh-cookie.same-site", "Lax");
        environment.setProperty("app.security.refresh-cookie.secure", "false");

        assertThatCode(() -> bind(environment)).doesNotThrowAnyException();
    }

    @Test
    void bind_unrecognisedSameSite_isRejected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.security.refresh-cookie.same-site", "Loose");

        assertThatThrownBy(() -> bind(environment)).isInstanceOf(BindException.class);
    }

    @Test
    void bind_blankName_isRejected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.security.refresh-cookie.name", "");

        assertThatThrownBy(() -> bind(environment)).isInstanceOf(BindException.class);
    }

    @Test
    void prodProfile_sameSiteAndSecureAreReadFromTheEnvironment() {
        // Production is the only profile where these two attributes matter. A profile-level
        // hardcode silently outranks the operator's environment variables, and the browser then
        // withholds the cookie with no server-side error, which is the failure mode the
        // same-site guard exists to prevent.
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "REFRESH_COOKIE_SAME_SITE=None",
                        "REFRESH_COOKIE_SECURE=true")
                .withUserConfiguration(RefreshCookiePropertiesConfig.class)
                .run(
                        context -> {
                            RefreshCookieProperties properties =
                                    context.getBean(RefreshCookieProperties.class);
                            assertThat(properties.sameSite()).isEqualTo("None");
                            assertThat(properties.secure()).isTrue();
                        });
    }

    @Test
    void prodProfile_noOverrides_fallsBackToFailClosedDefaults() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(RefreshCookiePropertiesConfig.class)
                .run(
                        context -> {
                            RefreshCookieProperties properties =
                                    context.getBean(RefreshCookieProperties.class);
                            assertThat(properties.secure()).isTrue();
                            assertThat(properties.sameSite()).isEqualTo("Lax");
                        });
    }

    @Test
    void devProfile_keepsSecureFalseForPlainHttpLocalhost() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=dev")
                .withUserConfiguration(RefreshCookiePropertiesConfig.class)
                .run(
                        context ->
                                assertThat(context.getBean(RefreshCookieProperties.class).secure())
                                        .isFalse());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RefreshCookieProperties.class)
    static class RefreshCookiePropertiesConfig {}

    private static RefreshCookieProperties bind(MockEnvironment environment) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return new Binder(ConfigurationPropertySources.get(environment))
                .bindOrCreate(
                        "app.security.refresh-cookie",
                        Bindable.of(RefreshCookieProperties.class),
                        new ValidationBindHandler(validator));
    }
}
