package com.app.modules.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import com.app.common.config.security.SecurityProperties;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String TEST_SIGNING_SECRET = "test-insecure-signing-secret-for-unit-tests";

    @Mock private Environment environment;

    private CookieOAuth2AuthorizationRequestRepository repository;

    @BeforeEach
    void setUp() {
        lenient().when(environment.matchesProfiles("prod")).thenReturn(false);
        repository = newRepository(TEST_SIGNING_SECRET);
    }

    // ── load ──────────────────────────────────────────────────────────────

    @Test
    void loadAuthorizationRequest_noCookies_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadAuthorizationRequest_unrelatedCookie_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other_cookie", "value"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadAuthorizationRequest_invalidBase64_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
                        "!!!not-valid-base64!!!"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadAuthorizationRequest_unsignedCookieValue_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(
                        CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
                        "dGhpcyBoYXMgbm8gc2lnbmF0dXJl"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadAuthorizationRequest_tamperedSignature_returnsNull() {
        OAuth2AuthorizationRequest original = buildAuthorizationRequest("state-tamper-sig");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
        String cookieValue = extractCookieValue(saveResponse.getHeader("Set-Cookie"));

        int dot = cookieValue.lastIndexOf('.');
        String tamperedValue =
                cookieValue.substring(0, dot + 1) + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(
                new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, tamperedValue));

        assertThat(repository.loadAuthorizationRequest(loadRequest)).isNull();
    }

    @Test
    void loadAuthorizationRequest_tamperedPayload_returnsNull() {
        OAuth2AuthorizationRequest original = buildAuthorizationRequest("state-tamper-payload");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
        String cookieValue = extractCookieValue(saveResponse.getHeader("Set-Cookie"));

        int dot = cookieValue.lastIndexOf('.');
        String tamperedPayload = "dGFtcGVyZWRwYXlsb2Fk";
        String tamperedValue = tamperedPayload + cookieValue.substring(dot);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(
                new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, tamperedValue));

        assertThat(repository.loadAuthorizationRequest(loadRequest)).isNull();
    }

    @Test
    void loadAuthorizationRequest_signedWithDifferentSecret_returnsNull() {
        CookieOAuth2AuthorizationRequestRepository other =
                newRepository("other-signing-secret-that-is-at-least-32ch");
        OAuth2AuthorizationRequest original = buildAuthorizationRequest("state-wrong-key");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        other.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);
        String cookieValue = extractCookieValue(saveResponse.getHeader("Set-Cookie"));

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(
                new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));

        assertThat(repository.loadAuthorizationRequest(loadRequest)).isNull();
    }

    @Test
    void loadAuthorizationRequest_afterSave_returnsEquivalentRequest() {
        OAuth2AuthorizationRequest original = buildAuthorizationRequest("state-xyz");
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

        String setCookieHeader = saveResponse.getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        String cookieValue = extractCookieValue(setCookieHeader);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(
                new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-xyz");
        assertThat(loaded.getClientId()).isEqualTo("test-client");
        assertThat(loaded.getScopes()).containsExactlyInAnyOrder("openid", "email");
    }

    // ── save ──────────────────────────────────────────────────────────────

    @Test
    void saveAuthorizationRequest_setsHttpOnlyInHeader() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                buildAuthorizationRequest("s1"), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
    }

    @Test
    void saveAuthorizationRequest_setsSameSiteLax() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                buildAuthorizationRequest("s1"), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    void saveAuthorizationRequest_setsPathRoot() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                buildAuthorizationRequest("s1"), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("Path=/");
    }

    @Test
    void saveAuthorizationRequest_setsMaxAge300() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                buildAuthorizationRequest("s1"), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=300");
    }

    @Test
    void saveAuthorizationRequest_nullRequest_expiresCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void saveAuthorizationRequest_nonProdProfile_doesNotSetSecureFlag() {
        when(environment.matchesProfiles("prod")).thenReturn(false);
        repository = newRepository(TEST_SIGNING_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                buildAuthorizationRequest("s1"), new MockHttpServletRequest(), response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).doesNotContain(";Secure").doesNotContain("; Secure");
    }

    @Test
    void saveAuthorizationRequest_prodProfile_setsSecureFlag() {
        when(environment.matchesProfiles("prod")).thenReturn(true);
        repository = newRepository(TEST_SIGNING_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
                buildAuthorizationRequest("s1"), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).containsIgnoringCase("Secure");
    }

    // ── remove ────────────────────────────────────────────────────────────

    @Test
    void removeAuthorizationRequest_noCookie_returnsNull() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest result =
                repository.removeAuthorizationRequest(new MockHttpServletRequest(), response);

        assertThat(result).isNull();
    }

    @Test
    void removeAuthorizationRequest_withCookie_returnsRequestAndClearsCookie() {
        OAuth2AuthorizationRequest original = buildAuthorizationRequest("remove-state");
        MockHttpServletRequest saveReq = new MockHttpServletRequest();
        MockHttpServletResponse saveResp = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, saveReq, saveResp);
        String cookieValue = extractCookieValue(saveResp.getHeader("Set-Cookie"));

        MockHttpServletRequest removeReq = new MockHttpServletRequest();
        removeReq.setCookies(
                new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));
        MockHttpServletResponse removeResp = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed =
                repository.removeAuthorizationRequest(removeReq, removeResp);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("remove-state");
        assertThat(removeResp.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CookieOAuth2AuthorizationRequestRepository newRepository(String secret) {
        SecurityProperties props = new SecurityProperties(List.of(), 2048, secret);
        return new CookieOAuth2AuthorizationRequestRepository(
                new ObjectMapper(), environment, props);
    }

    private static OAuth2AuthorizationRequest buildAuthorizationRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .clientId("test-client")
                .redirectUri("https://example.com/callback")
                .scopes(Set.of("openid", "email"))
                .state(state)
                .build();
    }

    /** Extracts the cookie value from a {@code Set-Cookie} header string. */
    private static String extractCookieValue(String setCookieHeader) {
        String nameValuePair = setCookieHeader.split(";")[0];
        return nameValuePair.substring(nameValuePair.indexOf('=') + 1);
    }
}
