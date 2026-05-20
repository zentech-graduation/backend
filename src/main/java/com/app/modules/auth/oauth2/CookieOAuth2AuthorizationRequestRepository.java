package com.app.modules.auth.oauth2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import com.app.common.config.security.SecurityProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Stores the pending {@link OAuth2AuthorizationRequest} in a short-lived HTTP cookie rather than in
 * the HTTP session, preserving the {@code SessionCreationPolicy.STATELESS} contract.
 *
 * <p>Security invariant: the cookie is {@code HttpOnly} (inaccessible to JavaScript), scoped to
 * {@code Path=/}, and carries {@code SameSite=Lax} to block cross-site request forgery against the
 * OAuth2 callback. The {@code Secure} attribute is enabled when the active Spring profile is {@code
 * prod}, ensuring the cookie is transmitted only over TLS in production. The {@code state}
 * parameter inside the serialized request is validated by Spring Security's OAuth2 callback
 * machinery, providing CSRF protection equivalent to — and compatible with — the default {@code
 * HttpSessionOAuth2AuthorizationRequestRepository}.
 *
 * <p>Cookie integrity: the cookie value uses the format {@code <payload>.<signature>}, where {@code
 * payload} is the Base64URL-encoded JSON of the authorization request and {@code signature} is its
 * HMAC-SHA256 digest (Base64URL-encoded, no padding) computed with the key configured via {@code
 * app.security.cookie-signing-secret}. On read, the server splits on {@code .}, recomputes the
 * HMAC, and performs a constant-time comparison; cookies whose signature is absent or invalid are
 * silently rejected, preventing state-parameter forgery.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements org.springframework.security.oauth2.client.web.AuthorizationRequestRepository<
                OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_AGE_SECONDS = 300;

    private final ObjectMapper objectMapper;
    private final boolean secureCookie;
    private final SecretKeySpec signingKey;

    public CookieOAuth2AuthorizationRequestRepository(
            ObjectMapper objectMapper,
            Environment environment,
            SecurityProperties securityProperties) {
        this.objectMapper = objectMapper;
        this.secureCookie = environment.matchesProfiles("prod");
        this.signingKey =
                new SecretKeySpec(
                        securityProperties.cookieSigningSecret().getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256");
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return findCookie(request)
                .map(Cookie::getValue)
                .flatMap(this::extractVerifiedPayload)
                .map(this::deserializePayload)
                .orElse(null);
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
                        .path("/api/v1/auth/oauth2")
                        .maxAge(MAX_AGE_SECONDS)
                        .build()
                        .toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expireCookie(response);
        return authorizationRequest;
    }

    private void expireCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path("/api/v1/auth/oauth2")
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
            String payload =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            return payload + "." + sign(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    /**
     * Splits {@code cookieValue} on the first {@code .}, recomputes the HMAC of the payload, and
     * returns the payload when the signatures match. Returns empty when the separator is absent or
     * the signature does not match.
     */
    private Optional<String> extractVerifiedPayload(String cookieValue) {
        int dot = cookieValue.indexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String payload = cookieValue.substring(0, dot);
        String providedSig = cookieValue.substring(dot + 1);
        try {
            byte[] expected = Base64.getUrlDecoder().decode(sign(payload));
            byte[] provided = Base64.getUrlDecoder().decode(providedSig);
            if (!MessageDigest.isEqual(expected, provided)) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(payload);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute cookie HMAC", e);
        }
    }

    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest deserializePayload(String payload) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
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
