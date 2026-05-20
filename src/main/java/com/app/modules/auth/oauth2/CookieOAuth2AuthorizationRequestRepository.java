package com.app.modules.auth.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Stores the pending {@link OAuth2AuthorizationRequest} in a short-lived HTTP cookie rather than in
 * the HTTP session, preserving the {@code SessionCreationPolicy.STATELESS} contract.
 *
 * <p>Security invariant: the cookie is {@code HttpOnly} (inaccessible to JavaScript), scoped to
 * {@code Path=/}, and carries {@code SameSite=Lax} to block cross-site request forgery against the
 * OAuth2 callback. The {@code Secure} attribute is enabled when the active Spring profile is {@code
 * prod}, ensuring the cookie is transmitted only over TLS in production. The serialized
 * authorization request is Base64url-encoded JSON; the {@code state} parameter inside it is
 * validated by Spring Security's OAuth2 callback machinery, providing CSRF protection equivalent to
 * — and compatible with — the default {@code HttpSessionOAuth2AuthorizationRequestRepository}.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements org.springframework.security.oauth2.client.web.AuthorizationRequestRepository<
                OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_AGE_SECONDS = 300;

    private final ObjectMapper objectMapper;
    private final boolean secureCookie;

    public CookieOAuth2AuthorizationRequestRepository(
            ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.secureCookie = environment.matchesProfiles("prod");
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return findCookie(request).map(Cookie::getValue).map(this::deserialize).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            expireCookie(response);
            return;
        }
        String value = serialize(authorizationRequest);
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE_NAME, value)
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(MAX_AGE_SECONDS)
                        .build()
                        .toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            expireCookie(response);
        }
        return authorizationRequest;
    }

    private void expireCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(0)
                        .build()
                        .toString());
    }

    private Optional<Cookie> findCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(c -> COOKIE_NAME.equals(c.getName())).findFirst();
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authorizationUri", authorizationRequest.getAuthorizationUri());
        data.put("grantType", authorizationRequest.getGrantType().getValue());
        data.put("responseType", authorizationRequest.getResponseType().getValue());
        data.put("clientId", authorizationRequest.getClientId());
        data.put("redirectUri", authorizationRequest.getRedirectUri());
        data.put("scopes", authorizationRequest.getScopes());
        data.put("state", authorizationRequest.getState());
        data.put("additionalParameters", authorizationRequest.getAdditionalParameters());
        data.put("attributes", authorizationRequest.getAttributes());
        data.put("authorizationRequestUri", authorizationRequest.getAuthorizationRequestUri());
        try {
            String json = objectMapper.writeValueAsString(data);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            Map<String, Object> data = objectMapper.readValue(decoded, Map.class);

            String grantType = (String) data.get("grantType");
            if (!AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
                return null;
            }

            List<String> scopesList = (List<String>) data.get("scopes");
            Set<String> scopes = scopesList != null ? new LinkedHashSet<>(scopesList) : Set.of();
            Map<String, Object> additionalParameters =
                    (Map<String, Object>) data.getOrDefault("additionalParameters", Map.of());
            Map<String, Object> attributes =
                    (Map<String, Object>) data.getOrDefault("attributes", Map.of());

            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri((String) data.get("authorizationUri"))
                    .clientId((String) data.get("clientId"))
                    .redirectUri((String) data.get("redirectUri"))
                    .scopes(scopes)
                    .state((String) data.get("state"))
                    .additionalParameters(additionalParameters)
                    .attributes(attributes)
                    .authorizationRequestUri((String) data.get("authorizationRequestUri"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
