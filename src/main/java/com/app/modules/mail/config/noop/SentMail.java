package com.app.modules.mail.config.noop;

/**
 * One message the {@code noop} transport was asked to deliver.
 *
 * <p>Captured verbatim, after template rendering, so a test can assert on exactly what a real
 * transport would have sent without any transport actually sending it.
 */
public record SentMail(String toEmail, String subject, String htmlBody) {}
