package com.app.common.mail.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.enums.MailTemplate;
import com.app.modules.mail.service.impl.SmtpMailSender;
import com.app.modules.mail.util.MailTemplateRenderer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class SmtpMailSenderTest {

    private static final String FROM_ADDRESS = "noreply@example.com";
    private static final String FROM_NAME = "Social";
    private static final String APP_NAME = "Social";
    private static final String TO_EMAIL = "user@example.com";
    private static final String TO_NAME = "Jane Doe";
    private static final String RENDERED_HTML = "<html>rendered</html>";

    @Mock private JavaMailSender javaMailSender;

    @Mock private MailTemplateRenderer mailTemplateRenderer;

    private SmtpMailSender service;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties();
        properties.setFromAddress(FROM_ADDRESS);
        properties.setFromName(FROM_NAME);
        properties.setAppName(APP_NAME);
        properties.setFrontendBaseUrl("http://localhost:3000");
        service = new SmtpMailSender(javaMailSender, properties, mailTemplateRenderer);
        when(javaMailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        when(mailTemplateRenderer.render(any(MailTemplate.class), any())).thenReturn(RENDERED_HTML);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendEmailVerification_assemblesCorrectVariableMapAndDispatchesEmail() throws Exception {
        service.sendEmailVerification(TO_EMAIL, TO_NAME, "https://app.local/verify?t=xyz");

        ArgumentCaptor<Map<String, Object>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mailTemplateRenderer)
                .render(eq(MailTemplate.EMAIL_VERIFICATION), varCaptor.capture());
        Map<String, Object> vars = varCaptor.getValue();
        assertThat(vars)
                .containsEntry("toName", TO_NAME)
                .containsEntry("appName", APP_NAME)
                .containsEntry("verificationUrl", "https://app.local/verify?t=xyz")
                .containsKey("expiryHours");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getFrom()[0].toString()).isEqualTo(FROM_NAME + " <" + FROM_ADDRESS + ">");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo(TO_EMAIL);
        assertThat(sent.getSubject()).isEqualTo("Verify your email address");
        assertThat(sent.getContent()).isEqualTo(RENDERED_HTML);
        // The real sender calls saveChanges before transmitting, which is what writes the
        // Content-Type header; without it the message still reports the default text/plain.
        sent.saveChanges();
        assertThat(sent.getContentType()).contains("text/html");
    }

    @Test
    void sendPasswordReset_dispatchesEmailWithResetSubject() throws Exception {
        service.sendPasswordReset(TO_EMAIL, TO_NAME, "https://app.local/reset?t=abc");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Reset your password");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo(TO_EMAIL);
    }

    @Test
    void send_whenTransportThrows_rethrowsAsServiceUnavailable() {
        doThrow(new MailSendException("sink unreachable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendWelcome(TO_EMAIL, TO_NAME))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ApiErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void sendWelcome_failure_logDoesNotContainRawEmailAddress() {
        doThrow(new MailSendException("sink unreachable"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        Logger logger = (Logger) LoggerFactory.getLogger(SmtpMailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service.sendWelcome(TO_EMAIL, TO_NAME))
                    .isInstanceOf(AppException.class);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .isNotEmpty()
                .noneMatch(event -> event.getFormattedMessage().contains(TO_EMAIL));
    }

    @Test
    void sendWelcome_success_logDoesNotContainRawEmailAddress() {
        Logger logger = (Logger) LoggerFactory.getLogger(SmtpMailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sendWelcome(TO_EMAIL, TO_NAME);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .isNotEmpty()
                .noneMatch(event -> event.getFormattedMessage().contains(TO_EMAIL));
    }

    private MimeMessage captureSentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }
}
