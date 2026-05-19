package com.app.modules.mail.enums;

import lombok.Getter;

/** Transactional email templates with their Thymeleaf path and default subject line. */
@Getter
public enum MailTemplate {
    // spotless:off
    EMAIL_VERIFICATION       ("mail/email-verification",        "Verify your email address"),
    PASSWORD_RESET           ("mail/password-reset",            "Reset your password"),
    WELCOME                  ("mail/welcome",                   "Welcome to Social"),
    PASSWORD_CHANGED         ("mail/password-changed",          "Your password has been changed"),
    OAUTH_ACCOUNT_NO_PASSWORD("mail/oauth-account-no-password", "Sign in with Google to access your account");
    // spotless:on

    private final String templatePath;
    private final String defaultSubject;

    MailTemplate(String templatePath, String defaultSubject) {
        this.templatePath = templatePath;
        this.defaultSubject = defaultSubject;
    }
}
