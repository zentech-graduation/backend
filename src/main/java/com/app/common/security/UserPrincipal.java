package com.app.common.security;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal kept on the {@code SecurityContext}. The role string mirrors {@link
 * com.app.modules.auth.enums.UserRole#name()} and is converted to a {@code ROLE_*} authority for
 * Spring Security's hierarchical role-based checks.
 */
public record UserPrincipal(UUID userId, String email, String role, String status)
        implements UserDetails, Principal {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"BANNED".equals(status) && !"SUSPENDED".equals(status);
    }

    @Override
    public boolean isAccountNonExpired() {
        return !"DEACTIVATED".equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
