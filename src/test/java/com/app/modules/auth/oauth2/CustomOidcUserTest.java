package com.app.modules.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.app.modules.users.entity.User;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserTest {

    @Mock private OidcUser delegate;
    @Mock private User user;

    private CustomOidcUser customOidcUser;

    @BeforeEach
    void setUp() {
        customOidcUser = new CustomOidcUser(delegate, user);
    }

    @Test
    void getUser_returnsInjectedUser() {
        assertThat(customOidcUser.getUser()).isSameAs(user);
    }

    @Test
    void getName_delegatesToDelegate() {
        when(delegate.getName()).thenReturn("sub-123");

        assertThat(customOidcUser.getName()).isEqualTo("sub-123");
        verify(delegate).getName();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAuthorities_delegatesToDelegate() {
        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        when(delegate.getAuthorities()).thenReturn((Collection) authorities);

        assertThat(customOidcUser.getAuthorities()).isEqualTo(authorities);
    }

    @Test
    void getClaims_delegatesToDelegate() {
        Map<String, Object> claims = Map.of("sub", "user-1");
        when(delegate.getClaims()).thenReturn(claims);

        assertThat(customOidcUser.getClaims()).isEqualTo(claims);
    }

    @Test
    void getAttributes_delegatesToDelegate() {
        Map<String, Object> attributes = Map.of("email", "user@example.com");
        when(delegate.getAttributes()).thenReturn(attributes);

        assertThat(customOidcUser.getAttributes()).isEqualTo(attributes);
    }
}
