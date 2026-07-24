package com.app.common.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.common.ApiConstants;
import com.app.common.config.redis.RateLimitProperties;
import com.app.common.config.security.SecurityProperties;
import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.service.RateLimiterService;
import com.app.common.security.util.CachedBodyHttpServletRequest;
import com.app.common.security.util.IpExtractor;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Rejects abusive traffic on configured endpoints before it reaches downstream filters or
 * controllers. The bucket key is namespaced per-client: per-IP for the login path (with the login
 * identifier appended so a single-account brute force cannot hide behind a rotating IP counter);
 * per {@code method:path:ip} for all other endpoints.
 *
 * <p>Rule resolution uses an exact-match fast path first, then falls back to {@link AntPathMatcher}
 * so path-variable routes (e.g. {@code /posts/{id}/likes}) can be configured without requiring
 * exact-match entries for every concrete path.
 *
 * <p>All 429 responses include a {@code Retry-After} header set to the matched rule's window.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGIN;

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final IpExtractor ipExtractor;
    private final SecurityProperties securityProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthRateLimitFilter(
            RateLimiterService rateLimiterService,
            RateLimitProperties properties,
            ObjectMapper objectMapper,
            IpExtractor ipExtractor,
            SecurityProperties securityProperties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.ipExtractor = ipExtractor;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitProperties.Rule rule = resolveRule(path, method);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest delivered = request;
        String ip = ipExtractor.extract(request);
        String key;

        if (LOGIN_PATH.equals(path) && "POST".equalsIgnoreCase(method)) {
            // Wrap so the controller can still read the body after we peek at the email.
            CachedBodyHttpServletRequest cached;
            try {
                cached =
                        new CachedBodyHttpServletRequest(
                                request, securityProperties.maxLoginBodyBytes());
            } catch (IOException e) {
                if (e.getMessage() != null
                        && e.getMessage().startsWith("Request body exceeds maximum")) {
                    writeBadRequestResponse(response);
                    return;
                }
                // Filters run before DispatcherServlet, so GlobalExceptionHandler never sees
                // this — an uncaught throw here would bypass the ApiResponse envelope entirely.
                log.warn("Unexpected IOException reading login request body", e);
                writeInternalErrorResponse(response);
                return;
            }
            delivered = cached;
            String identifier = extractIdentifier(cached.getCachedBody());
            key = path + ":" + ip + (identifier != null ? ":" + identifier : "");
        } else {
            key = method + ":" + path + ":" + ip;
        }

        if (!rateLimiterService.isAllowed(key, rule.maxAttempts(), rule.windowSeconds())) {
            writeRateLimitResponse(response, rule.windowSeconds());
            return;
        }

        chain.doFilter(delivered, response);
    }

    /**
     * Returns the matching {@link RateLimitProperties.Rule} or {@code null} if not rate-limited.
     *
     * <p>Resolution order:
     *
     * <ol>
     *   <li>Exact match (O(1)) — covers all literal auth paths and any other exactly-specified
     *       rules.
     *   <li>Ant-pattern match — covers path-variable templates such as {@code /posts/{id}/likes}.
     * </ol>
     */
    RateLimitProperties.Rule resolveRule(String path, String method) {
        // Fast path: exact key lookup used for all literal paths (e.g. auth endpoints).
        RateLimitProperties.Rule exact = properties.endpointRules().get(path);
        if (exact != null) {
            return exact;
        }
        // Pattern fallback: supports path-variable templates in endpointRules keys.
        for (Map.Entry<String, RateLimitProperties.Rule> entry :
                properties.endpointRules().entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns whether the given path + method combination is subject to rate limiting. */
    boolean isRateLimited(String path, String method) {
        return resolveRule(path, method) != null;
    }

    @SuppressWarnings("unchecked")
    private String extractIdentifier(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            Object identifier = parsed.get("identifier");
            if (identifier instanceof String s && StringUtils.hasText(s)) {
                return s.trim().toLowerCase();
            }
        } catch (RuntimeException ex) {
            // Malformed body — fall back to IP-only bucket.
        }
        return null;
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(), ApiResponse.failure(ApiErrorCode.TOO_MANY_REQUESTS));
    }

    private void writeBadRequestResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(), ApiResponse.failure(ApiErrorCode.BAD_REQUEST));
    }

    private void writeInternalErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(), ApiResponse.failure(ApiErrorCode.INTERNAL_ERROR));
    }
}
