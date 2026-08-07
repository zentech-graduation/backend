package com.app.common.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
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
