package com.app.modules.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.app.common.config.security.RefreshCookieProperties;
import com.app.common.security.jwt.JwtProperties;

class RefreshTokenCookieManagerTest {

    private static final long REFRESH_TTL_SECONDS = 2592000L;

    @Test
    void write_rendersEveryConfiguredAttribute() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager(true, "Lax").write(response, "RAW-TOKEN");

        assertThat(setCookieHeader(response))
                .contains("luvax_refresh=RAW-TOKEN")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=2592000")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    void write_maxAgeIsDerivedFromRefreshTokenTtl() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshCookieProperties cookieProperties =
                new RefreshCookieProperties("luvax_refresh", "/api/v1/auth", true, "Lax", false);
        JwtProperties jwtProperties =
                new JwtProperties("secret", "issuer", "audience", 900L, 1234L);

        new RefreshTokenCookieManager(cookieProperties, jwtProperties).write(response, "RAW");

        assertThat(setCookieHeader(response)).contains("Max-Age=1234");
    }

    @Test
    void write_secureFalse_omitsSecureAttributeForPlainHttpLocalhost() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager(false, "Lax").write(response, "RAW-TOKEN");

        assertThat(setCookieHeader(response)).doesNotContain("Secure");
    }

    @Test
    void clear_matchesWriteOnEveryAttributeThatBrowsersUseToIdentifyTheCookie() {
        // A browser removes a cookie only when name, path, secure, and same-site all match the
        // original. A clear that differs on any of them adds a second cookie instead of removing.
        MockHttpServletResponse written = new MockHttpServletResponse();
        MockHttpServletResponse cleared = new MockHttpServletResponse();
        RefreshTokenCookieManager manager = manager(true, "Lax");

        manager.write(written, "RAW-TOKEN");
        manager.clear(cleared);

        assertThat(setCookieHeader(cleared))
                .contains("luvax_refresh=")
                .contains("Path=/api/v1/auth")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Max-Age=0");
        assertThat(setCookieHeader(written)).contains("Path=/api/v1/auth").contains("SameSite=Lax");
    }

    @Test
    void read_cookiePresent_returnsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("luvax_refresh", "COOKIE-TOKEN"));

        assertThat(manager(true, "Lax").read(request)).contains("COOKIE-TOKEN");
    }

    @Test
    void read_noCookiesAtAll_returnsEmpty() {
        assertThat(manager(true, "Lax").read(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void read_differentCookieName_returnsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("some_other_cookie", "VALUE"));

        assertThat(manager(true, "Lax").read(request)).isEmpty();
    }

    @Test
    void read_cookiePresentButBlank_returnsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("luvax_refresh", ""));

        assertThat(manager(true, "Lax").read(request)).isEmpty();
    }

    @Test
    void resolve_bodyTokenPresent_bodyWinsOverCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("luvax_refresh", "COOKIE-TOKEN"));

        assertThat(manager(true, "Lax").resolve("BODY-TOKEN", request)).isEqualTo("BODY-TOKEN");
    }

    @Test
    void resolve_bodyTokenBlank_fallsBackToCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("luvax_refresh", "COOKIE-TOKEN"));

        assertThat(manager(true, "Lax").resolve("   ", request)).isEqualTo("COOKIE-TOKEN");
    }

    @Test
    void resolve_bodyTokenNull_fallsBackToCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("luvax_refresh", "COOKIE-TOKEN"));

        assertThat(manager(true, "Lax").resolve(null, request)).isEqualTo("COOKIE-TOKEN");
    }

    @Test
    void resolve_neitherSource_returnsEmptyStringNotNull() {
        // Downstream, RefreshTokenServiceImpl.rotate hashes the raw token before any null check,
        // so returning null here would surface as a 500 where the contract promises a 401.
        String resolved = manager(true, "Lax").resolve(null, new MockHttpServletRequest());

        assertThat(resolved).isNotNull().isEmpty();
    }

    private static RefreshTokenCookieManager manager(boolean secure, String sameSite) {
        RefreshCookieProperties cookieProperties =
                new RefreshCookieProperties(
                        "luvax_refresh",
                        "/api/v1/auth",
                        secure,
                        sameSite,
                        // Mirrors the acknowledgement the validator requires for None, so the
                        // constructed value is one the binder would actually accept.
                        "None".equals(sameSite));
        JwtProperties jwtProperties =
                new JwtProperties("secret", "issuer", "audience", 900L, REFRESH_TTL_SECONDS);
        return new RefreshTokenCookieManager(cookieProperties, jwtProperties);
    }

    private static String setCookieHeader(MockHttpServletResponse response) {
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }
}
