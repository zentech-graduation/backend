package com.app.modules.mail.enums;

import lombok.Getter;

/**
 * Moderation notice templates, kept separate from {@link MailTemplate} so the auth mail path is
 * untouched by anything here.
 *
 * <p>Every subject and every body states the action and the kind of content affected and nothing
 * else. The reason recorded on the audit row is written for colleagues and never reaches a
 * recipient, the acting staff member is never named, and no notice mentions that a report exists.
 *
 * <p>{@code showsStandardsLine} is false for the two reinstating actions: telling someone their
 * account is back and then telling them it fell short of the community standards reads as a
 * punishment rather than as the reversal it is.
 */
@Getter
public enum ModerationMailTemplate {
    // spotless:off
    ACCOUNT_BANNED          ("mail/moderation/account-banned",           "Your account has been banned",              true),
    ACCOUNT_REINSTATED      ("mail/moderation/account-reinstated",       "Your account has been reinstated",          false),
    ACCOUNT_SUSPENDED       ("mail/moderation/account-suspended",        "Your account has been suspended",           true),
    ACCOUNT_SUSPENSION_ENDED("mail/moderation/account-suspension-ended", "Your account suspension has ended",         false),
    ACCOUNT_WARNING         ("mail/moderation/account-warning",          "A warning has been added to your account",  true),
    POST_REMOVED            ("mail/moderation/content-removed-post",     "Your post was removed",                     true),
    COMMENT_REMOVED         ("mail/moderation/content-removed-comment",  "Your comment was removed",                  true),
    STORY_REMOVED           ("mail/moderation/content-removed-story",    "Your story was removed",                    true),
    MESSAGE_REMOVED         ("mail/moderation/content-removed-message",  "Your message was removed",                  true);
    // spotless:on

    private final String templatePath;
    private final String defaultSubject;
    private final boolean showsStandardsLine;

    ModerationMailTemplate(String templatePath, String defaultSubject, boolean showsStandardsLine) {
        this.templatePath = templatePath;
        this.defaultSubject = defaultSubject;
        this.showsStandardsLine = showsStandardsLine;
    }
}
