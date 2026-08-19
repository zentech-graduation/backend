package com.app.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.admin.service.RoleTransitionPolicy.Outcome;
import com.app.modules.users.enums.UserRole;

class RoleTransitionPolicyTest {

    private final RoleTransitionPolicy policy = new RoleTransitionPolicy();

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Test
    void evaluate_userToModerator_isAllowed() {
        assertThat(
                        policy.evaluate(
                                ACTOR, UserRole.ADMIN, TARGET, UserRole.USER, UserRole.MODERATOR))
                .isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void evaluate_moderatorToUser_isAllowed() {
        assertThat(
                        policy.evaluate(
                                ACTOR, UserRole.ADMIN, TARGET, UserRole.MODERATOR, UserRole.USER))
                .isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void evaluate_moderatorToAdmin_isAllowed() {
        assertThat(
                        policy.evaluate(
                                ACTOR, UserRole.ADMIN, TARGET, UserRole.MODERATOR, UserRole.ADMIN))
                .isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void evaluate_userToAdmin_isSkipLevel() {
        assertThat(policy.evaluate(ACTOR, UserRole.ADMIN, TARGET, UserRole.USER, UserRole.ADMIN))
                .isEqualTo(Outcome.SKIP_LEVEL);
    }

    @Test
    void evaluate_adminTargetToModerator_isTargetIsAdmin() {
        assertThat(
                        policy.evaluate(
                                ACTOR, UserRole.ADMIN, TARGET, UserRole.ADMIN, UserRole.MODERATOR))
                .isEqualTo(Outcome.TARGET_IS_ADMIN);
    }

    @Test
    void evaluate_adminTargetToUser_isTargetIsAdmin() {
        assertThat(policy.evaluate(ACTOR, UserRole.ADMIN, TARGET, UserRole.ADMIN, UserRole.USER))
                .isEqualTo(Outcome.TARGET_IS_ADMIN);
    }

    @Test
    void evaluate_userToUser_isNoOp() {
        assertThat(policy.evaluate(ACTOR, UserRole.ADMIN, TARGET, UserRole.USER, UserRole.USER))
                .isEqualTo(Outcome.NO_OP);
    }

    @Test
    void evaluate_moderatorToModerator_isNoOp() {
        assertThat(
                        policy.evaluate(
                                ACTOR,
                                UserRole.ADMIN,
                                TARGET,
                                UserRole.MODERATOR,
                                UserRole.MODERATOR))
                .isEqualTo(Outcome.NO_OP);
    }

    // The protected-target rule is checked before the no-op rule, so an administrator asked to stay
    // an administrator reports the more specific reason rather than "nothing to do".
    @Test
    void evaluate_adminToAdmin_reportsProtectedTargetRatherThanNoOp() {
        assertThat(policy.evaluate(ACTOR, UserRole.ADMIN, TARGET, UserRole.ADMIN, UserRole.ADMIN))
                .isEqualTo(Outcome.TARGET_IS_ADMIN);
    }

    @Test
    void evaluate_actorTargetsItself_isSelfTarget() {
        assertThat(
                        policy.evaluate(
                                ACTOR, UserRole.ADMIN, ACTOR, UserRole.ADMIN, UserRole.MODERATOR))
                .isEqualTo(Outcome.SELF_TARGET);
    }

    @Test
    void evaluate_moderatorActor_isActorNotAdmin() {
        assertThat(
                        policy.evaluate(
                                ACTOR,
                                UserRole.MODERATOR,
                                TARGET,
                                UserRole.USER,
                                UserRole.MODERATOR))
                .isEqualTo(Outcome.ACTOR_NOT_ADMIN);
    }

    @Test
    void evaluate_userActor_isActorNotAdmin() {
        assertThat(policy.evaluate(ACTOR, UserRole.USER, TARGET, UserRole.USER, UserRole.MODERATOR))
                .isEqualTo(Outcome.ACTOR_NOT_ADMIN);
    }

    // Actor authority is checked before self-targeting, so a non-administrator acting on itself is
    // refused for lacking authority rather than for the target being itself.
    @Test
    void evaluate_nonAdminActorTargetingItself_reportsActorNotAdmin() {
        assertThat(policy.evaluate(ACTOR, UserRole.USER, ACTOR, UserRole.USER, UserRole.MODERATOR))
                .isEqualTo(Outcome.ACTOR_NOT_ADMIN);
    }

    @Test
    void assertAllowed_permittedTransition_doesNotThrow() {
        assertThatCode(
                        () ->
                                policy.assertAllowed(
                                        ACTOR,
                                        UserRole.ADMIN,
                                        TARGET,
                                        UserRole.USER,
                                        UserRole.MODERATOR))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_nonAdminActor_throwsForbidden() {
        assertThatThrownBy(
                        () ->
                                policy.assertAllowed(
                                        ACTOR,
                                        UserRole.MODERATOR,
                                        TARGET,
                                        UserRole.USER,
                                        UserRole.MODERATOR))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.FORBIDDEN);
    }

    @Test
    void assertAllowed_selfTarget_throwsSelfActionNotAllowed() {
        assertThatThrownBy(
                        () ->
                                policy.assertAllowed(
                                        ACTOR,
                                        UserRole.ADMIN,
                                        ACTOR,
                                        UserRole.ADMIN,
                                        UserRole.MODERATOR))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.ADMIN_SELF_ACTION_NOT_ALLOWED);
    }

    @Test
    void assertAllowed_adminTarget_throwsRoleTransitionForbidden() {
        assertThatThrownBy(
                        () ->
                                policy.assertAllowed(
                                        ACTOR,
                                        UserRole.ADMIN,
                                        TARGET,
                                        UserRole.ADMIN,
                                        UserRole.MODERATOR))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.ADMIN_ROLE_TRANSITION_NOT_ALLOWED);
    }

    @Test
    void assertAllowed_skipLevel_throwsRoleTransitionForbidden() {
        assertThatThrownBy(
                        () ->
                                policy.assertAllowed(
                                        ACTOR,
                                        UserRole.ADMIN,
                                        TARGET,
                                        UserRole.USER,
                                        UserRole.ADMIN))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.ADMIN_ROLE_TRANSITION_NOT_ALLOWED);
    }

    @Test
    void assertAllowed_noOp_throwsRoleTransitionForbidden() {
        assertThatThrownBy(
                        () ->
                                policy.assertAllowed(
                                        ACTOR,
                                        UserRole.ADMIN,
                                        TARGET,
                                        UserRole.USER,
                                        UserRole.USER))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.ADMIN_ROLE_TRANSITION_NOT_ALLOWED);
    }
}
