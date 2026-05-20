package com.app.common.security.filter;

import java.io.IOException;

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

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.security.jwt.JwtClaims;
import com.app.common.security.jwt.JwtTokenProvider;
import com.app.common.security.service.TokenBlacklistService;
import com.app.common.security.user.SecurityMapper;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.enums.UserStatus;
import com.app.modules.auth.repository.UserRepository;

/**
 * Authenticates requests by extracting a Bearer JWT, verifying its signature, and resolving the
 * persisted {@link User} so that account-level state (status, soft-delete) is enforced on every
 * call. The raw token is stored as the {@link UsernamePasswordAuthenticationToken} credentials so
 * that downstream handlers (logout) can recover the {@code jti} and remaining lifetime without
 * re-reading the {@code Authorization} header.
 *
 * <p>The filter never writes the response on failure: it clears the context and lets downstream
 * handlers (Spring Security's {@code AuthenticationEntryPoint}) decide how to respond.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final SecurityMapper securityMapper;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            SecurityMapper securityMapper,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.securityMapper = securityMapper;
        this.tokenBlacklistService = tokenBlacklistService;
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

        try {
            String token = header.substring(BEARER_PREFIX.length());
            JwtClaims claims = jwtTokenProvider.validateAndParse(token);

            if (tokenBlacklistService.isBlacklisted(claims.jti())) {
                SecurityContextHolder.clearContext();
                chain.doFilter(req, res);
                return;
            }

            User user =
                    userRepository
                            .findByIdAndDeletedAtIsNull(claims.userId())
                            .orElseThrow(() -> new AppException(ApiErrorCode.AUTH_TOKEN_INVALID));

            // We bypass DaoAuthenticationProvider here (token-based path), so
            // UserDetails.isEnabled()/isAccountNonLocked() on UserPrincipal are never
            // consulted by Spring Security. Enforce account status explicitly.
            if (user.getStatus() != UserStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                chain.doFilter(req, res);
                return;
            }

            UserPrincipal principal = securityMapper.toUserPrincipal(user);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, token, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (AppException ex) {
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(req, res);
    }
}
