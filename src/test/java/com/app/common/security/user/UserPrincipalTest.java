package com.app.common.security.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserPrincipalTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void isEnabled_activeStatus_returnsTrue() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "user", "ACTIVE");
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_inactiveStatus_returnsFalse() {
        UserPrincipal principal =
                new UserPrincipal(USER_ID, "user@example.com", "user", "SUSPENDED");
        assertThat(principal.isEnabled()).isFalse();
    }

    @Test
    void isAccountNonLocked_activeStatus_returnsTrue() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "user", "ACTIVE");
        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    void isAccountNonLocked_bannedStatus_returnsFalse() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "user", "BANNED");
        assertThat(principal.isAccountNonLocked()).isFalse();
    }

    @Test
    void isAccountNonLocked_suspendedStatus_returnsFalse() {
        UserPrincipal principal =
                new UserPrincipal(USER_ID, "user@example.com", "user", "SUSPENDED");
        assertThat(principal.isAccountNonLocked()).isFalse();
    }

    @Test
    void isAccountNonExpired_activeStatus_returnsTrue() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "user", "ACTIVE");
        assertThat(principal.isAccountNonExpired()).isTrue();
    }

    @Test
    void isAccountNonExpired_deactivatedStatus_returnsFalse() {
        UserPrincipal principal =
                new UserPrincipal(USER_ID, "user@example.com", "user", "DEACTIVATED");
        assertThat(principal.isAccountNonExpired()).isFalse();
    }

    @Test
    void getName_returnsUserIdAsString() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "user", "ACTIVE");
        assertThat(principal.getName()).isEqualTo(USER_ID.toString());
    }

    @Test
    void getAuthorities_returnsRolePrefixed() {
        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "admin", "ACTIVE");
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_admin");
    }

    @Test
    void isCredentialsNonExpired_alwaysTrue() {
        UserPrincipal principal =
                new UserPrincipal(USER_ID, "user@example.com", "user", "DEACTIVATED");
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }
}
