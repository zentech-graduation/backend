package com.app.modules.auth.oauth2;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.app.modules.auth.service.OAuth2ExchangeCodeService;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.users.entity.User;

/**
 * Bridges a successful Google sign-in to the frontend back-channel exchange flow.
 *
 * <p>Generates a short-lived opaque exchange code for the authenticated user, stores it in Redis
 * with a 120-second TTL, and redirects the browser to {@code
 * {frontendBaseUrl}/oauth2/callback?code={exchangeCode}}. The frontend must immediately call {@code
 * POST /api/v1/auth/oauth2/exchange} to redeem the code for a token pair. Tokens are never exposed
 * in the browser redirect.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2ExchangeCodeService exchangeCodeService;
    private final MailProperties mailProperties;

    public OAuth2AuthenticationSuccessHandler(
            OAuth2ExchangeCodeService exchangeCodeService, MailProperties mailProperties) {
        this.exchangeCodeService = exchangeCodeService;
        this.mailProperties = mailProperties;
    }

    /** Stores an exchange code and redirects the browser to the frontend callback route. */
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        CustomOidcUser oidcUser = (CustomOidcUser) authentication.getPrincipal();
        User user = oidcUser.getUser();

        String exchangeCode = exchangeCodeService.storeExchangeCode(user.getId());
        response.sendRedirect(
                mailProperties.getFrontendBaseUrl() + "/oauth2/callback?code=" + exchangeCode);
    }
}
