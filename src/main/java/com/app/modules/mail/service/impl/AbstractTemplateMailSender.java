package com.app.modules.mail.service.impl;

import java.util.HashMap;
import java.util.Map;

import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.enums.MailTemplate;
import com.app.modules.mail.enums.ModerationMailTemplate;
import com.app.modules.mail.enums.SupportMailTemplate;
import com.app.modules.mail.service.MailSender;
import com.app.modules.mail.util.MailTemplateRenderer;

/**
 * Transport-neutral base for {@link MailSender} implementations.
 *
 * <p>Owns the single copy of every template variable map so a template gaining a variable is a
 * one-line change for all transports. Subclasses see only a recipient, a subject and a rendered
 * HTML body.
 */
public abstract class AbstractTemplateMailSender implements MailSender {

    private static final int EMAIL_VERIFICATION_EXPIRY_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRY_MINUTES = 15;

    private final MailProperties mailProperties;
    private final MailTemplateRenderer mailTemplateRenderer;

    protected AbstractTemplateMailSender(
            MailProperties mailProperties, MailTemplateRenderer mailTemplateRenderer) {
        this.mailProperties = mailProperties;
        this.mailTemplateRenderer = mailTemplateRenderer;
    }

    /**
     * Delivers an already rendered message through the concrete transport.
     *
     * <p>Implementations must translate every transport failure into {@code
     * ApiErrorCode.SERVICE_UNAVAILABLE} so callers observe one failure shape, and must never log
     * the recipient address or any raw token.
     *
     * @param toEmail recipient email address
     * @param subject message subject line
     * @param htmlBody rendered HTML body
     * @return the provider's identifier for the accepted message, or null when the transport has
     *     none; the send log stores it so a delivery can be traced at the provider afterwards
     */
    protected abstract String deliver(String toEmail, String subject, String htmlBody);

    /**
     * Builds the {@code From} header value shared by every transport.
     *
     * @return the configured sender name and address in {@code Name <address>} form
     */
    protected String fromHeader() {
        return mailProperties.getFromName() + " <" + mailProperties.getFromAddress() + ">";
    }

    @Override
    public void sendEmailVerification(String toEmail, String toName, String verificationUrl) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        variables.put("verificationUrl", verificationUrl);
        variables.put("expiryHours", EMAIL_VERIFICATION_EXPIRY_HOURS);
        render(MailTemplate.EMAIL_VERIFICATION, variables, toEmail);
    }

    @Override
    public void sendPasswordReset(String toEmail, String toName, String resetUrl) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        variables.put("resetUrl", resetUrl);
        variables.put("expiryMinutes", PASSWORD_RESET_EXPIRY_MINUTES);
        render(MailTemplate.PASSWORD_RESET, variables, toEmail);
    }

    @Override
    public void sendWelcome(String toEmail, String toName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        render(MailTemplate.WELCOME, variables, toEmail);
    }

    @Override
    public void sendPasswordChanged(String toEmail, String toName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", toName);
        variables.put("appName", mailProperties.getAppName());
        render(MailTemplate.PASSWORD_CHANGED, variables, toEmail);
    }

    @Override
    public void sendOAuthAccountNoPassword(String toEmail, String displayName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("toName", displayName);
        variables.put("appName", mailProperties.getAppName());
        variables.put("frontendBaseUrl", mailProperties.getFrontendBaseUrl());
        render(MailTemplate.OAUTH_ACCOUNT_NO_PASSWORD, variables, toEmail);
    }

    private void render(MailTemplate template, Map<String, Object> variables, String toEmail) {
        String html = mailTemplateRenderer.render(template, variables);
        deliver(toEmail, template.getDefaultSubject(), html);
    }

    /**
     * Renders and delivers the public-form confirmation link.
     *
     * @param variables Thymeleaf variables for the confirmation template
     * @param toEmail the unproven address the submitter gave
     * @return the provider's identifier for the accepted message, or null
     */
    /**
     * Renders and delivers one campaign mail.
     *
     * @param subject the administrator-authored subject
     * @param bodyHtml the already-sanitized body from {@code CampaignBodyRenderer}
     * @param unsubscribeUrl the recipient's opt-out link, supplied by the application
     * @param toEmail recipient email address
     * @return the provider's identifier for the accepted message, or null
     */
    public String sendCampaign(
            String subject, String bodyHtml, String unsubscribeUrl, String toEmail) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", subject);
        variables.put("appName", mailProperties.getAppName());
        variables.put("bodyHtml", bodyHtml);
        variables.put("unsubscribeUrl", unsubscribeUrl);
        return deliver(toEmail, subject, mailTemplateRenderer.renderCampaign(variables));
    }

    public String sendSupportConfirmation(Map<String, Object> variables, String toEmail) {
        String html =
                mailTemplateRenderer.render(SupportMailTemplate.CONFIRM_SUPPORT_REQUEST, variables);
        return deliver(
                toEmail, SupportMailTemplate.CONFIRM_SUPPORT_REQUEST.getDefaultSubject(), html);
    }

    /**
     * Renders and delivers one moderation notice, returning the provider identifier.
     *
     * @param template the moderation template to render
     * @param variables Thymeleaf variables for that template
     * @param toEmail recipient email address
     * @return the provider's identifier for the accepted message, or null when the transport has
     *     none
     */
    public String sendModerationNotice(
            ModerationMailTemplate template, Map<String, Object> variables, String toEmail) {
        String html = mailTemplateRenderer.render(template, variables);
        return deliver(toEmail, template.getDefaultSubject(), html);
    }
}
