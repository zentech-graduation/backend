package com.app.common.mail.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.app.common.config.MailProperties;
import com.app.modules.mail.MailSendException;
import com.app.modules.mail.impl.MailServiceImpl;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTest {

    private static final String FROM_ADDRESS = "noreply@example.com";
    private static final String FROM_NAME = "Example";
    private static final String TO_EMAIL = "user@example.com";
    private static final String TO_NAME = "Jane Doe";
    private static final String RENDERED_HTML = "<html>rendered</html>";

    @Mock private Resend resend;

    @Mock private Emails emails;

    @Mock private TemplateEngine templateEngine;

    private MailServiceImpl service;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties(FROM_ADDRESS, FROM_NAME);
        service = new MailServiceImpl(resend, properties, templateEngine);
        when(resend.emails()).thenReturn(emails);
        when(templateEngine.process(any(String.class), any(Context.class)))
                .thenReturn(RENDERED_HTML);
    }

    @Test
    void sendEmailVerification_rendersTemplateAndDispatchesViaResend() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(mock(CreateEmailResponse.class));

        service.sendEmailVerification(TO_EMAIL, TO_NAME, "https://app.local/verify?t=xyz");

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/email-verification"), ctxCaptor.capture());
        Context ctx = ctxCaptor.getValue();
        assertThat(ctx.getVariable("recipientName")).isEqualTo(TO_NAME);
        assertThat(ctx.getVariable("verificationUrl")).isEqualTo("https://app.local/verify?t=xyz");
        assertThat(ctx.getVariable("expiryHours"))
                .isEqualTo(MailServiceImpl.EMAIL_VERIFICATION_EXPIRY_HOURS);

        ArgumentCaptor<CreateEmailOptions> paramsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(paramsCaptor.capture());
        CreateEmailOptions params = paramsCaptor.getValue();
        assertThat(params.getFrom()).isEqualTo(FROM_NAME + " <" + FROM_ADDRESS + ">");
        assertThat(params.getTo()).containsExactly(TO_EMAIL);
        assertThat(params.getSubject()).isEqualTo("Verify your email address");
        assertThat(params.getHtml()).isEqualTo(RENDERED_HTML);
    }

    @Test
    void sendPasswordReset_usesPasswordResetTemplateAndExpiryMinutes() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(mock(CreateEmailResponse.class));

        service.sendPasswordReset(TO_EMAIL, TO_NAME, "https://app.local/reset?t=abc");

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/password-reset"), ctxCaptor.capture());
        Context ctx = ctxCaptor.getValue();
        assertThat(ctx.getVariable("recipientName")).isEqualTo(TO_NAME);
        assertThat(ctx.getVariable("resetUrl")).isEqualTo("https://app.local/reset?t=abc");
        assertThat(ctx.getVariable("expiryMinutes"))
                .isEqualTo(MailServiceImpl.PASSWORD_RESET_EXPIRY_MINUTES);

        ArgumentCaptor<CreateEmailOptions> paramsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getSubject()).isEqualTo("Reset your password");
    }

    @Test
    void sendWelcome_rendersWelcomeTemplate() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(mock(CreateEmailResponse.class));

        service.sendWelcome(TO_EMAIL, TO_NAME);

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/welcome"), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue().getVariable("recipientName")).isEqualTo(TO_NAME);

        ArgumentCaptor<CreateEmailOptions> paramsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getSubject()).isEqualTo("Welcome to VietRecruit");
    }

    @Test
    void sendPasswordChanged_includesTimestampVariable() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(mock(CreateEmailResponse.class));

        service.sendPasswordChanged(TO_EMAIL, TO_NAME);

        ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("mail/password-changed"), ctxCaptor.capture());
        Context ctx = ctxCaptor.getValue();
        assertThat(ctx.getVariable("recipientName")).isEqualTo(TO_NAME);
        assertThat(ctx.getVariable("changeTimestamp"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} UTC");

        ArgumentCaptor<CreateEmailOptions> paramsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getSubject())
                .isEqualTo("Your password has been changed");
    }

    @Test
    void send_wrapsResendExceptionAsMailSendException() throws Exception {
        doThrow(new ResendException("upstream failure"))
                .when(emails)
                .send(any(CreateEmailOptions.class));

        assertThatThrownBy(() -> service.sendWelcome(TO_EMAIL, TO_NAME))
                .isInstanceOf(MailSendException.class)
                .hasCauseInstanceOf(ResendException.class);
    }
}
