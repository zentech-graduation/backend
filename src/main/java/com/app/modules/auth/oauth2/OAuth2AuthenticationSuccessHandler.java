package com.app.modules.auth.oauth2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.IpExtractor;
import com.app.common.security.JwtProperties;
import com.app.common.security.JwtTokenProvider;
import com.app.common.security.RefreshTokenService;
import com.app.modules.auth.dto.response.AuthResponse;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.mapper.AuthMapper;
import com.app.modules.auth.repository.UserCredentialRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Issues a JWT access + refresh pair after a successful Google sign-in and writes the standard
 * {@link ApiResponse} JSON envelope. No browser redirect is performed because the front-end is not
 * yet wired.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_DEVICE_ID = "oauth2-web";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final UserCredentialRepository userCredentialRepository;
    private final ObjectMapper objectMapper;
    private final AuthMapper authMapper;
    private final IpExtractor ipExtractor;

    public OAuth2AuthenticationSuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            JwtProperties jwtProperties,
            UserCredentialRepository userCredentialRepository,
            ObjectMapper objectMapper,
            AuthMapper authMapper,
            IpExtractor ipExtractor) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.userCredentialRepository = userCredentialRepository;
        this.objectMapper = objectMapper;
        this.authMapper = authMapper;
        this.ipExtractor = ipExtractor;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        CustomOidcUser oidcUser = (CustomOidcUser) authentication.getPrincipal();
        User user = oidcUser.getUser();

        String deviceId = extractDeviceId(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String ipAddress = ipExtractor.extract(request);

        String accessToken =
                jwtTokenProvider.generateAccessToken(
                        user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken =
                refreshTokenService.issue(user.getId(), deviceId, userAgent, ipAddress);

        boolean emailVerified =
                userCredentialRepository
                        .findByUserId(user.getId())
                        .map(UserCredential::isEmailVerified)
                        .orElse(true);

        AuthResponse body =
                new AuthResponse(
                        accessToken,
                        refreshToken,
                        jwtProperties.accessTokenTtl(),
                        AuthResponse.BEARER,
                        authMapper.toUserSummaryResponse(user, emailVerified));

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.success(ApiSuccessCode.OK, body));
    }

    private static String extractDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-ID");
        return StringUtils.hasText(deviceId) ? deviceId : DEFAULT_DEVICE_ID;
    }
}
