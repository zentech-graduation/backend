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
 * per {@code path:ip} for all other endpoints, shared across every HTTP method that reaches the
 * matched key so a rule's configured budget is the total across methods, not a multiple of it.
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

        RuleMatch match = resolveRuleMatch(path, method);
        if (match == null) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitProperties.Rule rule = match.rule();

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
            // Bucket on the matched rule key, not the concrete request path: a SockJS transport
            // negotiation embeds a random server/session segment in the URL on every connection
            // attempt, so keying on the concrete path would hand every attempt a fresh bucket.
            // Exact-match rules are unaffected since their matched key equals the concrete path.
            // No method in the key: a rule's configured budget applies to the endpoint as a whole,
            // not once per HTTP method that happens to reach it.
            key = match.matchedKey() + ":" + ip;
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
        RuleMatch match = resolveRuleMatch(path, method);
        return match == null ? null : match.rule();
    }

    /** Returns whether the given path + method combination is subject to rate limiting. */
    boolean isRateLimited(String path, String method) {
        return resolveRule(path, method) != null;
    }

    /**
     * Resolves both the applicable {@link RateLimitProperties.Rule} and the {@code endpointRules}
     * key that matched, so the caller can bucket on the matched key rather than the concrete
     * request path.
     *
     * <p>Resolution order:
     *
     * <ol>
     *   <li>Exact match (O(1)) — covers all literal auth paths and any other exactly-specified
     *       rules. The matched key equals the concrete path.
     *   <li>Ant-pattern match — covers path-variable templates such as {@code /posts/{id}/likes}
     *       and wildcard surfaces such as {@code /ws/**}. The matched key is the configured
     *       pattern, not the concrete path. When more than one pattern matches, the most specific
     *       one wins, so a broad sub-tree rule never shadows a narrower rule written for one
     *       expensive endpoint inside it.
     * </ol>
     */
    private RuleMatch resolveRuleMatch(String path, String method) {
        RateLimitProperties.Rule exact = properties.endpointRules().get(path);
        if (exact != null) {
            return new RuleMatch(path, exact);
        }
        // Most specific pattern wins rather than whichever the map happens to yield first. Map
        // iteration order is the YAML declaration order, which makes a rule's budget depend on
        // where somebody added it in the file; with a broad /api/v1/admin/** rule alongside
        // narrower ones that is a silent mis-budgeting rather than a visible mistake.
        String bestPattern = null;
        for (String pattern : properties.endpointRules().keySet()) {
            if (!pathMatcher.match(pattern, path)) {
                continue;
            }
            if (bestPattern == null
                    || pathMatcher.getPatternComparator(path).compare(pattern, bestPattern) < 0) {
                bestPattern = pattern;
            }
        }
        return bestPattern == null
                ? null
                : new RuleMatch(bestPattern, properties.endpointRules().get(bestPattern));
    }

    private record RuleMatch(String matchedKey, RateLimitProperties.Rule rule) {}

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
