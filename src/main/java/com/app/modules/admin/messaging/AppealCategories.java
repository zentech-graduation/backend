package com.app.modules.admin.messaging;

import java.util.Map;

import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.support.enums.SupportCategory;

/**
 * Which appeal a moderation action can be contested under.
 *
 * <p>Only the six punitive actions are appealable. The two reinstating actions are not - there is
 * nothing to contest about being unbanned - and neither are the two support ticket notices, which
 * would otherwise let a rejected appeal be appealed in an unbounded loop.
 *
 * <p>The category is decided here rather than by the submitter, and travels inside the single-use
 * token. A client-supplied category would let someone appeal a decision the token never authorised.
 */
public final class AppealCategories {

    private static final Map<AdminActionType, SupportCategory> BY_ACTION =
            Map.of(
                    AdminActionType.BAN_USER, SupportCategory.APPEAL_BAN,
                    AdminActionType.SUSPEND_USER, SupportCategory.APPEAL_SUSPENSION,
                    AdminActionType.WARN_USER, SupportCategory.APPEAL_WARNING_STRIKE,
                    AdminActionType.REMOVE_POST, SupportCategory.APPEAL_CONTENT_REMOVAL,
                    AdminActionType.REMOVE_COMMENT, SupportCategory.APPEAL_CONTENT_REMOVAL,
                    AdminActionType.REMOVE_STORY, SupportCategory.APPEAL_CONTENT_REMOVAL,
                    AdminActionType.REMOVE_MESSAGE, SupportCategory.APPEAL_CONTENT_REMOVAL);

    private AppealCategories() {}

    /**
     * Resolves the appeal category for one action.
     *
     * @param actionType the recorded moderation action
     * @return the category, or null when the action carries no appeal link
     */
    public static SupportCategory forAction(AdminActionType actionType) {
        return BY_ACTION.get(actionType);
    }
}
