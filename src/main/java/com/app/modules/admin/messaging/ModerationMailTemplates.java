package com.app.modules.admin.messaging;

import java.util.Map;

import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.mail.enums.ModerationMailTemplate;

/**
 * The closed mapping from a moderation action to the notice its subject receives.
 *
 * <p>Nine actions produce mail. Every other action type is absent on purpose, and the absences
 * carry as much intent as the entries:
 *
 * <ul>
 *   <li>No {@code RESTORE_*} action mails. Telling someone their content is back draws attention to
 *       a removal they may never have noticed, and the restore is not a decision they need to act
 *       on.
 *   <li>{@code ISSUE_STRIKE} does not mail. A strike always accompanies a status change that mails
 *       already, and the warning that produced it mailed when it was issued, so a third message
 *       would say nothing new.
 *   <li>{@code FORCE_LOGOUT}, {@code REVOKE_SESSION}, {@code CHANGE_USER_ROLE}, {@code
 *       REVOKE_WARNING}, {@code REVOKE_STRIKE}, {@code ESCALATE_REPORT} and every hashtag action do
 *       not mail. They are either invisible to the account or internal to the moderation queue.
 * </ul>
 */
public final class ModerationMailTemplates {

    private static final Map<AdminActionType, ModerationMailTemplate> BY_ACTION =
            Map.of(
                    AdminActionType.BAN_USER, ModerationMailTemplate.ACCOUNT_BANNED,
                    AdminActionType.UNBAN_USER, ModerationMailTemplate.ACCOUNT_REINSTATED,
                    AdminActionType.SUSPEND_USER, ModerationMailTemplate.ACCOUNT_SUSPENDED,
                    AdminActionType.UNSUSPEND_USER, ModerationMailTemplate.ACCOUNT_SUSPENSION_ENDED,
                    AdminActionType.WARN_USER, ModerationMailTemplate.ACCOUNT_WARNING,
                    AdminActionType.REMOVE_POST, ModerationMailTemplate.POST_REMOVED,
                    AdminActionType.REMOVE_COMMENT, ModerationMailTemplate.COMMENT_REMOVED,
                    AdminActionType.REMOVE_STORY, ModerationMailTemplate.STORY_REMOVED,
                    AdminActionType.REMOVE_MESSAGE, ModerationMailTemplate.MESSAGE_REMOVED);

    private ModerationMailTemplates() {}

    /**
     * Resolves the notice template for one action.
     *
     * @param actionType the recorded moderation action
     * @return the template to render, or null when the action deliberately sends no mail
     */
    public static ModerationMailTemplate forAction(AdminActionType actionType) {
        return BY_ACTION.get(actionType);
    }

    /**
     * Reports whether an action produces a notice, so a caller can decide whether to enqueue one.
     *
     * @param actionType the recorded moderation action
     * @return true when this action mails its subject
     */
    public static boolean mails(AdminActionType actionType) {
        return BY_ACTION.containsKey(actionType);
    }
}
