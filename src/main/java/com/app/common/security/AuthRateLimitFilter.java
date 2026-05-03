package com.app.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.common.ApiConstants;
import com.app.common.config.redis.RateLimitProperties;
import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * Rejects abusive traffic on sensitive auth endpoints (login, forgot-password, resend-verification)
 * before it reaches downstream filters or controllers. The bucket key is always namespaced by
 * request IP; for login the JSON body's {@code email} field is appended so a single-account brute
 * force cannot be hidden behind a rotating IP-only counter.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGIN;
    private static final String FORGOT_PATH =
            ApiConstants.Auth.ROOT + ApiConstants.Auth.FORGOT_PASSWORD;
    private static final String RESEND_PATH =
            ApiConstants.Auth.ROOT + ApiConstants.Auth.RESEND_VERIFY;

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(
            RateLimiterService rateLimiterService,
            RateLimitProperties properties,
            ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method) || !isRateLimited(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest delivered = request;
        String ip = extractIp(request);
        String key;
        RateLimitProperties.Rule rule;

        if (LOGIN_PATH.equals(path)) {
            // Wrap so the controller can still read the body after we peek at the email.
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            delivered = cached;
            String email = extractEmail(cached.getCachedBody());
            key = path + ":" + ip + (email != null ? ":" + email : "");
            rule = properties.login();
        } else if (FORGOT_PATH.equals(path)) {
            key = path + ":" + ip;
            rule = properties.forgotPassword();
        } else {
            key = path + ":" + ip;
            rule = properties.resendVerification();
        }

        if (!rateLimiterService.isAllowed(key, rule.maxAttempts(), rule.windowSeconds())) {
            writeRateLimitResponse(response);
            return;
        }

        chain.doFilter(delivered, response);
    }

    private boolean isRateLimited(String path) {
        return LOGIN_PATH.equals(path) || FORGOT_PATH.equals(path) || RESEND_PATH.equals(path);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    @SuppressWarnings("unchecked")
    private String extractEmail(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            Object email = parsed.get("email");
            if (email instanceof String s && StringUtils.hasText(s)) {
                return s.trim().toLowerCase();
            }
        } catch (RuntimeException ex) {
            // Malformed body — fall back to IP-only bucket.
        }
        return null;
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.failure(ApiErrorCode.TOO_MANY_REQUESTS);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
