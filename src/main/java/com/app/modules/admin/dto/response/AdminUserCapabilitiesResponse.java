package com.app.modules.admin.dto.response;

import java.util.List;

import com.app.modules.users.enums.UserRole;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the requesting administrator may do to this account.
 *
 * <p>Present so a client can decide whether to render a control instead of discovering the answer
 * from a rejection after the user has clicked it. Without it a panel renders Ban, Suspend and
 * Change role for every account and writes its own copy of the rules to grey them out, which is one
 * more place for those rules to drift from the server's.
 *
 * <p>Derived from the component that enforces the rules, on every request, so it cannot disagree
 * with what the write endpoints will actually do.
 *
 * <p>Force logout carries no flag because it carries no per-target rule: any administrator may end
 * any live account's sessions, including another administrator's and its own.
 *
 * @param canChangeStatus whether ban, unban, suspend and unsuspend are permitted; the four share
 *     one rule, so one flag answers all of them
 * @param canChangeRole whether any role transition at all is permitted
 * @param assignableRoles the roles the account may be moved to, empty when none may be
 */
@Schema(description = "What the requesting administrator may do to this account")
public record AdminUserCapabilitiesResponse(
        @Schema(
                        description =
                                "Whether ban, unban, suspend and unsuspend are permitted. The four"
                                        + " share one rule, so one flag answers all of them.")
                boolean canChangeStatus,
        @Schema(description = "Whether any role transition at all is permitted")
                boolean canChangeRole,
        @Schema(
                        description =
                                "The roles this account may be moved to. Empty when none may be,"
                                        + " which is also when canChangeRole is false.")
                List<UserRole> assignableRoles) {}
