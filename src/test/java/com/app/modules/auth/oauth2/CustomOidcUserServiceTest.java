package com.app.modules.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.app.modules.auth.enums.OAuthProvider;
import com.app.modules.auth.repository.OAuthAccountRepository;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.validation.UserStateValidator;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSettingsRepository;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock private OAuthAccountRepository oauthAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository userCredentialRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UserStateValidator userStateValidator;

    private CustomOidcUserService service;

    @BeforeEach
    void setUp() {
        service =
                new CustomOidcUserService(
                        oauthAccountRepository,
                        userRepository,
                        userCredentialRepository,
                        userSettingsRepository,
                        userStateValidator);
    }

    private OidcUserRequest buildRequestForRegistrationId(String registrationId) {
        ClientRegistration clientRegistration =
                ClientRegistration.withRegistrationId(registrationId)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .clientId("client-id")
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .authorizationUri("https://example.com/oauth2/authorize")
                        .tokenUri("https://example.com/oauth2/token")
                        .userInfoUri("https://example.com/userinfo")
                        .userNameAttributeName("sub")
                        .jwkSetUri("https://example.com/.well-known/jwks.json")
                        .scope("openid", "email", "profile")
                        .build();

        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-token-value",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Set.of("openid", "email", "profile"));

        OidcIdToken idToken =
                new OidcIdToken(
                        "id-token-value",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Map.of("sub", "12345", "iss", "https://accounts.google.com"));

        return new OidcUserRequest(clientRegistration, accessToken, idToken);
    }

    @Test
    void resolveProvider_google_returnsGoogleProvider() {
        OidcUserRequest request = buildRequestForRegistrationId("google");

        OAuthProvider provider = service.resolveProvider(request);

        assertThat(provider).isEqualTo(OAuthProvider.GOOGLE);
    }

    @Test
    void resolveProvider_facebook_throwsOAuth2AuthenticationException() {
        OidcUserRequest request = buildRequestForRegistrationId("facebook");

        assertThatThrownBy(() -> service.resolveProvider(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("facebook");
    }

    @Test
    void resolveProvider_unknown_throwsOAuth2AuthenticationException() {
        OidcUserRequest request = buildRequestForRegistrationId("unknown");

        assertThatThrownBy(() -> service.resolveProvider(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("unknown");
    }
}
