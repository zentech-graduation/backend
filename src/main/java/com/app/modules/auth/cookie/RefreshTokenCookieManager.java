package com.app.modules.auth.cookie;

import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.app.common.config.security.RefreshCookieProperties;
import com.app.common.security.jwt.JwtProperties;

/**
 * Sole owner of the refresh-token cookie's lifecycle.
 *
 * <p>Security contract: the cookie is always {@code HttpOnly}, which is the entire reason it
 * exists. A browser client can hold its access token in memory and still restore a session after a
 * page reload, without the refresh credential ever being readable by application JavaScript.
 *
 * <p>CSRF invariant: CSRF filtering is disabled for {@code /api/**} and {@code /auth/refresh} is
 * anonymous, so {@code SameSite} is the only control that stops a hostile origin from driving a
 * rotation with the victim's cookie. {@code Lax} withholds the cookie from cross-site POSTs.
 * Relaxing it to {@code None} removes that protection and is justified only when the API and the
 * browser client occupy different registrable domains; the configuration binding then requires
 * {@code Secure} and fails startup without it.
 *
 * <p>Lifetime invariant: {@code Max-Age} is derived from {@code app.jwt.refresh-token-ttl}, the
 * same value that sets the persisted token's expiry, so the cookie can neither outlive nor
 * under-live the credential it carries.
 */
@Component
public class RefreshTokenCookieManager {

    private final RefreshCookieProperties properties;
    private final long maxAgeSeconds;

    public RefreshTokenCookieManager(
            RefreshCookieProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.maxAgeSeconds = jwtProperties.refreshTokenTtl();
    }

    /** Issues the cookie carrying {@code rawToken} for the configured refresh-token lifetime. */
    public void write(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(rawToken, maxAgeSeconds).toString());
    }

    /**
     * Expires the cookie. Every attribute a browser uses to identify a cookie must match the
     * issuing call, or the browser adds a second cookie instead of removing the first.
     */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", 0).toString());
    }

    /** Returns the cookie's value, or empty when it is absent or blank. */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    /**
     * Resolves the raw refresh token, preferring the request body so existing non-browser and
     * future mobile clients continue to work without change.
     *
     * @param bodyToken token supplied in the request body, may be null or blank
     * @param request current request, inspected for the cookie only when the body supplies nothing
     * @return the resolved token, or the empty string when neither source supplies one; never null,
     *     because the downstream rotation hashes this value before performing any null check
     */
    public String resolve(String bodyToken, HttpServletRequest request) {
        if (StringUtils.hasText(bodyToken)) {
            return bodyToken;
        }
        return read(request).orElse("");
    }

    private ResponseCookie build(String value, long maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(maxAge)
                .build();
    }
}
