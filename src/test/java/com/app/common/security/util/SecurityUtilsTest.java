package com.app.common.security.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_noAuthentication_throwsUnauthorized() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::getCurrentUser)
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ApiErrorCode.UNAUTHORIZED));
    }

    @Test
    void isAdmin_adminRole_returnsTrue() {
        setUpContext(UUID.randomUUID(), "ADMIN");

        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_nonAdminRole_returnsFalse() {
        setUpContext(UUID.randomUUID(), "user");

        assertThat(SecurityUtils.isAdmin()).isFalse();
    }

    private void setUpContext(UUID userId, String role) {
        var principal =
                new com.app.common.security.user.UserPrincipal(
                        userId, "user@example.com", role, "ACTIVE");
        var auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
