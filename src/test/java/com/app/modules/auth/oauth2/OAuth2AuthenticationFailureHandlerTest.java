package com.app.modules.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationFailureHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private OAuth2AuthenticationFailureHandler handler;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        handler = new OAuth2AuthenticationFailureHandler(new ObjectMapper());
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(request.getRequestURI()).thenReturn("/api/v1/auth/oauth2/callback/google");
    }

    @Test
    void onAuthenticationFailure_doesNotLeakExceptionMessage() throws Exception {
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token"),
                        "Invalid Google OIDC token: signature mismatch");

        handler.onAuthenticationFailure(request, response, exception);

        String body = responseBody.toString();
        assertThat(body).doesNotContain("Invalid Google OIDC token");
        assertThat(body).doesNotContain("signature mismatch");
    }

    @Test
    void onAuthenticationFailure_responseBodyContainsAuthTokenInvalidCode() throws Exception {
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token"),
                        "Invalid Google OIDC token: signature mismatch");

        handler.onAuthenticationFailure(request, response, exception);

        String body = responseBody.toString();
        assertThat(body).contains("AUTH_TOKEN_INVALID");
    }

    @Test
    void onAuthenticationFailure_sets401StatusCode() throws Exception {
        OAuth2AuthenticationException exception =
                new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token"), "some internal detail");

        handler.onAuthenticationFailure(request, response, exception);

        // Verify the correct HTTP status code was set on the response
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
