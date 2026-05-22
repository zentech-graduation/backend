package com.app.common.mail.service.impl;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.mail.service.MailSender;
import com.app.modules.mail.service.impl.MailServiceImpl;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTest {

    private static final String TO_EMAIL = "user@example.com";
    private static final String TO_NAME = "Jane Doe";

    @Mock private MailSender mailSender;

    private MailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MailServiceImpl(mailSender);
    }

    @Test
    void sendEmailVerification_delegatesToSynchronousSender() {
        String url = "https://app.local/verify?t=xyz";

        service.sendEmailVerification(TO_EMAIL, TO_NAME, url);

        verify(mailSender).sendEmailVerification(TO_EMAIL, TO_NAME, url);
    }

    @Test
    void sendPasswordReset_delegatesToSynchronousSender() {
        String url = "https://app.local/reset?t=xyz";

        service.sendPasswordReset(TO_EMAIL, TO_NAME, url);

        verify(mailSender).sendPasswordReset(TO_EMAIL, TO_NAME, url);
    }

    @Test
    void sendWelcome_delegatesToSynchronousSender() {
        service.sendWelcome(TO_EMAIL, TO_NAME);

        verify(mailSender).sendWelcome(TO_EMAIL, TO_NAME);
    }

    @Test
    void sendPasswordChanged_delegatesToSynchronousSender() {
        service.sendPasswordChanged(TO_EMAIL, TO_NAME);

        verify(mailSender).sendPasswordChanged(TO_EMAIL, TO_NAME);
    }

    @Test
    void sendOAuthAccountNoPassword_delegatesToSynchronousSender() {
        service.sendOAuthAccountNoPassword(TO_EMAIL, TO_NAME);

        verify(mailSender).sendOAuthAccountNoPassword(TO_EMAIL, TO_NAME);
    }
}
