package com.app.common.mail.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.config.noop.SentMail;
import com.app.modules.mail.config.noop.SentMailRecorder;
import com.app.modules.mail.enums.MailTemplate;
import com.app.modules.mail.service.impl.NoopMailSender;
import com.app.modules.mail.util.MailTemplateRenderer;

@ExtendWith(MockitoExtension.class)
class NoopMailSenderTest {

    private static final String FROM_ADDRESS = "noreply@example.com";
    private static final String FROM_NAME = "Social";
    private static final String APP_NAME = "Social";
    private static final String TO_EMAIL = "user@example.com";
    private static final String TO_NAME = "Jane Doe";
    private static final String RENDERED_HTML = "<html>rendered</html>";

    @Mock private MailTemplateRenderer mailTemplateRenderer;

    private SentMailRecorder sentMailRecorder;
    private NoopMailSender service;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties();
        properties.setFromAddress(FROM_ADDRESS);
        properties.setFromName(FROM_NAME);
        properties.setAppName(APP_NAME);
        properties.setFrontendBaseUrl("http://localhost:3000");
        sentMailRecorder = new SentMailRecorder();
        service = new NoopMailSender(properties, mailTemplateRenderer, sentMailRecorder);
        when(mailTemplateRenderer.render(any(MailTemplate.class), any(Map.class)))
                .thenReturn(RENDERED_HTML);
    }

    @Test
    void sendEmailVerification_capturesRenderedMessageWithoutSending() {
        service.sendEmailVerification(TO_EMAIL, TO_NAME, "https://app.local/verify?t=xyz");

        assertThat(sentMailRecorder.sent()).hasSize(1);
        SentMail sent = sentMailRecorder.sent().get(0);
        assertThat(sent.toEmail()).isEqualTo(TO_EMAIL);
        assertThat(sent.subject()).isEqualTo("Verify your email address");
        assertThat(sent.htmlBody()).isEqualTo(RENDERED_HTML);
    }

    @Test
    void sendPasswordReset_capturesResetSubject() {
        service.sendPasswordReset(TO_EMAIL, TO_NAME, "https://app.local/reset?t=abc");

        assertThat(sentMailRecorder.sent()).hasSize(1);
        assertThat(sentMailRecorder.sent().get(0).subject()).isEqualTo("Reset your password");
    }

    @Test
    void multipleSends_eachCaptured() {
        service.sendWelcome(TO_EMAIL, TO_NAME);
        service.sendPasswordChanged(TO_EMAIL, TO_NAME);

        assertThat(sentMailRecorder.sent()).hasSize(2);
    }
}
