package com.app.common.security.filter;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.common.security.service.TokenPrincipalResolver;
import com.app.common.security.user.UserPrincipal;

/**
 * Authenticates requests by extracting a Bearer JWT and resolving it to an authenticated principal
 * via {@link TokenPrincipalResolver}, which enforces signature validity, expiry, blacklist status,
 * and account status (banned/suspended/deactivated accounts never authenticate). The raw token is
 * stored as the {@link UsernamePasswordAuthenticationToken} credentials so that downstream handlers
 * (logout) can recover the {@code jti} and remaining lifetime without re-reading the {@code
 * Authorization} header.
 *
 * <p>The filter never writes the response on failure: it clears the context and lets downstream
 * handlers (Spring Security's {@code AuthenticationEntryPoint}) decide how to respond.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenPrincipalResolver tokenPrincipalResolver;

    public JwtAuthenticationFilter(TokenPrincipalResolver tokenPrincipalResolver) {
        this.tokenPrincipalResolver = tokenPrincipalResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        Optional<UserPrincipal> principal = tokenPrincipalResolver.resolve(token);
        if (principal.isPresent()) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal.get(), token, principal.get().getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(req, res);
    }
}
