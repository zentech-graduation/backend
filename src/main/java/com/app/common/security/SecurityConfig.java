package com.app.common.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.app.common.ApiConstants;
import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;
import com.app.modules.auth.oauth2.CustomOidcUserService;
import com.app.modules.auth.oauth2.OAuth2AuthenticationFailureHandler;
import com.app.modules.auth.oauth2.OAuth2AuthenticationSuccessHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Stateless Spring Security wiring for the modular monolith.
 *
 * <p>Declares exactly one {@link SecurityFilterChain} bean: the JWT-based REST chain that disables
 * sessions, registers {@link JwtAuthenticationFilter}, and routes authentication and authorization
 * failures back through the {@link ApiResponse} envelope. Authorization rules are organized by
 * semantic category via private helper methods invoked from {@link #securityFilterChain}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    private static final String[] PUBLIC_POST_AUTH_PATHS = {
        ApiConstants.Auth.ROOT + ApiConstants.Auth.REGISTER,
        ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGIN,
        ApiConstants.Auth.ROOT + ApiConstants.Auth.REFRESH,
        ApiConstants.Auth.ROOT + ApiConstants.Auth.FORGOT_PASSWORD,
        ApiConstants.Auth.ROOT + ApiConstants.Auth.RESET_PASSWORD,
        ApiConstants.Auth.ROOT + ApiConstants.Auth.RESEND_VERIFY,
    };

    private static final String[] AUTHENTICATED_POST_AUTH_PATHS = {
        ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGOUT,
        ApiConstants.Auth.ROOT + ApiConstants.Auth.CHANGE_PASSWORD,
    };

    private static final String[] PUBLIC_INFRA_PATHS = {
        "/actuator/health", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
    };

    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Autowired private ObjectMapper objectMapper;

    public SecurityConfig(
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthRateLimitFilter authRateLimitFilter,
            CustomOidcUserService customOidcUserService,
            OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler) {
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authRateLimitFilter = authRateLimitFilter;
        this.customOidcUserService = customOidcUserService;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
    }

    @PostConstruct
    void validateJwtSecret() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters; refusing to start with a weak"
                            + " key");
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(
                        auth -> {
                            configurePublicAuthEndpoints(auth);
                            configureOAuth2Endpoints(auth);
                            configureAuthenticatedEndpoints(auth);
                            configureInfrastructureEndpoints(auth);
                            configureRoleBasedEndpoints(auth);
                            auth.anyRequest().authenticated();
                        })
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class)
                .oauth2Login(
                        oauth2 ->
                                oauth2.authorizationEndpoint(
                                                ep -> ep.baseUri("/api/v1/auth/oauth2/authorize"))
                                        .redirectionEndpoint(
                                                ep -> ep.baseUri("/api/v1/auth/oauth2/callback/*"))
                                        .userInfoEndpoint(
                                                info -> info.oidcUserService(customOidcUserService))
                                        .successHandler(oAuth2AuthenticationSuccessHandler)
                                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(authenticationEntryPoint())
                                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    /** Permits anonymous access to token-issuing and account-management auth endpoints. */
    private void configurePublicAuthEndpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    auth) {
        auth.requestMatchers(HttpMethod.POST, PUBLIC_POST_AUTH_PATHS).permitAll();
        auth.requestMatchers(
                        HttpMethod.GET, ApiConstants.Auth.ROOT + ApiConstants.Auth.VERIFY_EMAIL)
                .permitAll();
    }

    /** Permits anonymous access to Spring OAuth2 client initiation and callback paths. */
    private void configureOAuth2Endpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    auth) {
        auth.requestMatchers(HttpMethod.GET, ApiConstants.Auth.ROOT + "/oauth2/**").permitAll();
        auth.requestMatchers(HttpMethod.POST, ApiConstants.Auth.ROOT + "/oauth2/**").permitAll();
    }

    /** Restricts session-scoped auth operations to authenticated users. */
    private void configureAuthenticatedEndpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    auth) {
        auth.requestMatchers(HttpMethod.POST, AUTHENTICATED_POST_AUTH_PATHS).authenticated();
    }

    /**
     * Opens health and API-documentation endpoints to all callers; restricts remaining actuator
     * endpoints to ADMIN.
     */
    private void configureInfrastructureEndpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    auth) {
        auth.requestMatchers(PUBLIC_INFRA_PATHS).permitAll();
        auth.requestMatchers("/actuator/**").hasRole("ADMIN");
    }

    /** Restricts admin API paths to ADMIN and moderator API paths to MODERATOR or ADMIN. */
    private void configureRoleBasedEndpoints(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
                    auth) {
        auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
        auth.requestMatchers("/api/v1/moderator/**").hasAnyRole("MODERATOR", "ADMIN");
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Refuse to fall back to a wildcard origin when allowCredentials=true. Empty config
        // becomes a deny-all CORS policy; operator must set CORS_ALLOWED_ORIGINS explicitly.
        configuration.setAllowedOrigins(parseOrigins(corsProperties.allowedOrigins()));
        configuration.setAllowedMethods(
                Arrays.asList(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "Accept-Language",
                        "X-Requested-With",
                        "X-Device-ID"));
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException ex) -> writeFailure(response, ApiErrorCode.UNAUTHORIZED);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request,
                HttpServletResponse response,
                AccessDeniedException ex) -> writeFailure(response, ApiErrorCode.FORBIDDEN);
    }

    private void writeFailure(HttpServletResponse response, ApiErrorCode errorCode)
            throws IOException, ServletException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode));
    }

    private static List<String> parseOrigins(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Stream.of(raw.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }
}
