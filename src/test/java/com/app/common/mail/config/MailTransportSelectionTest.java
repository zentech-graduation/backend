package com.app.common.mail.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.config.MailTransportGuard;
import com.app.modules.mail.service.MailSender;
import com.app.modules.mail.service.impl.MailServiceImpl;
import com.app.modules.mail.service.impl.ResendMailSender;
import com.app.modules.mail.service.impl.SmtpMailSender;
import com.app.modules.mail.util.MailTemplateRenderer;
import com.resend.Resend;

class MailTransportSelectionTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(MailProperties.class)
                    .withBean(MailTemplateRenderer.class, () -> mock(MailTemplateRenderer.class))
                    .withBean(Resend.class, () -> mock(Resend.class))
                    .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                    .withUserConfiguration(
                            MailTransportGuard.class,
                            ResendMailSender.class,
                            SmtpMailSender.class,
                            MailServiceImpl.class);

    @Test
    void resendTransport_registersOnlyTheResendSender() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.mail.transport=resend")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(MailSender.class);
                            assertThat(context).hasSingleBean(ResendMailSender.class);
                            assertThat(context).doesNotHaveBean(SmtpMailSender.class);
                        });
    }

    @Test
    void smtpTransport_registersOnlyTheSmtpSender() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.mail.transport=smtp")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(MailSender.class);
                            assertThat(context).hasSingleBean(SmtpMailSender.class);
                            assertThat(context).doesNotHaveBean(ResendMailSender.class);
                        });
    }

    @Test
    void absentTransport_failsNamingThePropertyAndTheAcceptedValues() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            // Asserting only that startup failed would pass on the defect being
                            // fixed here: absence used to reach Spring's generic "no qualifying
                            // bean of type MailSender" message, which gives an operator no reason
                            // to suspect the mail transport property.
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("app.mail.transport")
                                    .hasMessageContaining("resend")
                                    .hasMessageContaining("smtp");
                        });
    }

    @Test
    void unrecognisedTransport_failsNamingTheValueItFoundAndTheAcceptedValues() {
        runner.withPropertyValues("spring.profiles.active=dev", "app.mail.transport=sendgrid")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("app.mail.transport")
                                    .hasMessageContaining("sendgrid")
                                    .hasMessageContaining("resend")
                                    .hasMessageContaining("smtp");
                        });
    }

    @Test
    void smtpTransportOutsideDevelopment_refusesToStartAndNamesTransportAndProfiles() {
        runner.withPropertyValues("spring.profiles.active=prod", "app.mail.transport=smtp")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("smtp")
                                    .hasMessageContaining("[prod]");
                        });
    }

    @Test
    void resendTransportOutsideDevelopment_startsNormally() {
        runner.withPropertyValues("spring.profiles.active=prod", "app.mail.transport=resend")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(ResendMailSender.class);
                        });
    }

    @Test
    void smtpTransportWithNoActiveProfile_refusesToStart() {
        runner.withPropertyValues("app.mail.transport=smtp")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("smtp");
                        });
    }
}
